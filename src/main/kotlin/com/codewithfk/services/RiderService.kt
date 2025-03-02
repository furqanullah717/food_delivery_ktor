package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import com.codewithfk.services.OrderService.getOrderAddress
import com.google.maps.DirectionsApi
import com.google.maps.model.TravelMode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.*

object RiderService {
    private const val SEARCH_RADIUS_KM = 6371.0
    private const val EARTH_RADIUS_KM = 6371.0

    fun updateRiderLocation(riderId: UUID, latitude: Double, longitude: Double) {
        transaction {
            // Update or insert new location
            val existingLocation = RiderLocationsTable
                .select { RiderLocationsTable.riderId eq riderId }
                .firstOrNull()

            if (existingLocation != null) {
                RiderLocationsTable.update({ RiderLocationsTable.riderId eq riderId }) {
                    it[this.latitude] = latitude
                    it[this.longitude] = longitude
                    it[this.lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            } else {
                RiderLocationsTable.insert {
                    it[this.riderId] = riderId
                    it[this.latitude] = latitude
                    it[this.longitude] = longitude
                    it[this.isAvailable] = true
                    it[this.lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            }
        }
    }

    // Calculate distance between two points using Haversine formula
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + 
                cos(originLat) * cos(destinationLat) * 
                sin(dLon / 2).pow(2)
        
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_KM * c
    }

    fun findNearbyRiders(restaurantLat: Double, restaurantLng: Double): List<RiderLocation> {
        return transaction {
            RiderLocationsTable
                .select { RiderLocationsTable.isAvailable eq true }
                .map {
                    RiderLocation(
                        id = it[RiderLocationsTable.riderId].toString(),
                        latitude = it[RiderLocationsTable.latitude],
                        longitude = it[RiderLocationsTable.longitude],
                        isAvailable = it[RiderLocationsTable.isAvailable],
                        lastUpdated = it[RiderLocationsTable.lastUpdated].toString()
                    )
                }
                .filter { rider ->
                    calculateDistance(
                        restaurantLat, restaurantLng,
                        rider.latitude, rider.longitude
                    ) <= SEARCH_RADIUS_KM
                }
        }
    }

    fun createDeliveryRequest(orderId: UUID): Boolean {
        return transaction {
            try {
                val order = OrdersTable
                    .join(RestaurantsTable, JoinType.INNER, OrdersTable.restaurantId, RestaurantsTable.id)
                    .select { OrdersTable.id eq orderId }
                    .firstOrNull() ?: throw IllegalStateException("Order not found")

                val restaurantLat = order[RestaurantsTable.latitude] 
                    ?: throw IllegalStateException("Restaurant latitude not found")
                val restaurantLng = order[RestaurantsTable.longitude] 
                    ?: throw IllegalStateException("Restaurant longitude not found")

                val nearbyRiders = findNearbyRiders(restaurantLat, restaurantLng)
                
                if (nearbyRiders.isEmpty()) {
                    return@transaction false
                }

                // Create delivery requests for nearby riders
                nearbyRiders.forEach { rider ->
                    DeliveryRequestsTable.insert {
                        it[this.orderId] = orderId
                        it[this.riderId] = UUID.fromString(rider.id)
                        it[this.status] = "PENDING"
                        it[this.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }

                    // Notify rider
                    notifyRider(UUID.fromString(rider.id), orderId)
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun notifyRider(riderId: UUID, orderId: UUID) {
        val riderFcmToken = transaction {
            UsersTable
                .select { UsersTable.id eq riderId }
                .map { it[UsersTable.fcmToken] }
                .firstOrNull()
        }

        riderFcmToken?.let { token ->
            FirebaseService.sendNotification(
                token = token,
                title = "New Delivery Request",
                body = "New delivery request available",
                data = mapOf(
                    "type" to "DELIVERY_REQUEST",
                    "orderId" to orderId.toString()
                )
            )
        }
    }

    fun acceptDeliveryRequest(riderId: UUID, orderId: UUID): Boolean {
        return transaction {
            // Check if order is available (READY status and no assigned rider)
            val order = OrdersTable.select { 
                (OrdersTable.id eq orderId) and 
                (OrdersTable.status eq OrderStatus.READY.name) and
                (OrdersTable.riderId.isNull())
            }.firstOrNull() ?: return@transaction false
            
            // Update order to assign it to rider
            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.riderId] = riderId
                it[OrdersTable.status] = OrderStatus.ASSIGNED.name
                it[OrdersTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } > 0
            
            if (updated) {
                // Notify customer
                val customerId = order[OrdersTable.userId]
                NotificationService.createNotification(
                    userId = customerId,
                    title = "Delivery Update",
                    message = "Your order has been assigned to a rider and will be picked up soon",
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
                
                // Notify restaurant
                val restaurantId = order[OrdersTable.restaurantId]
                val restaurantOwnerId = RestaurantsTable
                    .select { RestaurantsTable.id eq restaurantId }
                    .map { it[RestaurantsTable.ownerId] }
                    .single()
                    
                NotificationService.createNotification(
                    userId = restaurantOwnerId,
                    title = "Rider Assigned",
                    message = "A rider has been assigned to pick up order #${orderId.toString().take(8)}",
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
            }
            
            updated
        }
    }

    fun rejectDeliveryRequest(riderId: UUID, orderId: UUID): Boolean {
        return transaction {
            // We don't modify the order directly when rejecting,
            // instead we track rejections in a new table to avoid showing
            // this delivery to this rider again
            
            // First check if the order is still available
            val orderExists = OrdersTable.select { 
                (OrdersTable.id eq orderId) and 
                (OrdersTable.status eq OrderStatus.READY.name) and
                (OrdersTable.riderId.isNull())
            }.count() > 0
            
            if (!orderExists) {
                return@transaction false
            }
            
            // Instead of using DeliveryRequestsTable, we'll track rejections in RiderRejections table
            // This table needs to be created if it doesn't exist
            RiderRejectionsTable.insert {
                it[this.riderId] = riderId
                it[this.orderId] = orderId
                it[this.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            }
            
            true
        }
    }

    fun getDeliveryPath(riderId: UUID, orderId: UUID): DeliveryPath {
        val order = OrderService.getOrderDetails(orderId)
        val riderLocation = getRiderLocation(riderId)
        val restaurant = RestaurantService.getRestaurantById(UUID.fromString(order.restaurantId))
            ?: throw IllegalStateException("Restaurant not found")
        val customerAddress = order.address 
            ?: throw IllegalStateException("Customer address not found")

        val isPickedUp = order.status == OrderStatus.OUT_FOR_DELIVERY.name

        // Get directions
        val directions = if (!isPickedUp) {
            // Rider -> Restaurant -> Customer
            DirectionsApi.newRequest(GeocodingService.geoApiContext)
                .mode(TravelMode.DRIVING)
                .origin(com.google.maps.model.LatLng(riderLocation.latitude, riderLocation.longitude))
                .destination(com.google.maps.model.LatLng(customerAddress.latitude!!, customerAddress.longitude!!))
                .waypoints(com.google.maps.model.LatLng(restaurant.latitude!!, restaurant.longitude!!))
                .await()
        } else {
            // Rider -> Customer
            DirectionsApi.newRequest(GeocodingService.geoApiContext)
                .mode(TravelMode.DRIVING)
                .origin(com.google.maps.model.LatLng(riderLocation.latitude, riderLocation.longitude))
                .destination(com.google.maps.model.LatLng(customerAddress.latitude!!, customerAddress.longitude!!))
                .await()
        }

        return createDeliveryPathFromDirections(
            directions = directions,
            riderLocation = riderLocation,
            restaurant = restaurant,
            customerAddress = customerAddress,
            isPickedUp = isPickedUp
        )
    }

    private fun createDeliveryPathFromDirections(
        directions: com.google.maps.model.DirectionsResult,
        riderLocation: RiderLocation,
        restaurant: Restaurant,
        customerAddress: Address,
        isPickedUp: Boolean
    ): DeliveryPath = DeliveryPath(
        currentLocation = Location(
            latitude = riderLocation.latitude,
            longitude = riderLocation.longitude,
            address = "Rider's current location"
        ),
        nextStop = if (!isPickedUp) {
            Location(
                latitude = restaurant.latitude!!,
                longitude = restaurant.longitude!!,
                address = restaurant.address!!
            )
        } else {
            Location(
                latitude = customerAddress.latitude!!,
                longitude = customerAddress.longitude!!,
                address = customerAddress.addressLine1
            )
        },
        finalDestination = Location(
            latitude = customerAddress.latitude!!,
            longitude = customerAddress.longitude!!,
            address = customerAddress.addressLine1
        ),
        polyline = directions.routes[0].overviewPolyline.encodedPath,
        estimatedTime = directions.routes[0].legs.sumOf { it.duration.inSeconds.toInt() } / 60,
        deliveryPhase = if (isPickedUp) DeliveryPhase.TO_CUSTOMER else DeliveryPhase.TO_RESTAURANT
    )

    fun getRiderLocation(riderId: UUID): RiderLocation {
        return transaction {
            RiderLocationsTable
                .select { RiderLocationsTable.riderId eq riderId }
                .orderBy(RiderLocationsTable.lastUpdated, SortOrder.DESC)
                .limit(1)
                .map {
                    RiderLocation(
                        id = it[RiderLocationsTable.riderId].toString(),
                        latitude = it[RiderLocationsTable.latitude],
                        longitude = it[RiderLocationsTable.longitude],
                        isAvailable = it[RiderLocationsTable.isAvailable],
                        lastUpdated = it[RiderLocationsTable.lastUpdated].toString()
                    )
                }
                .firstOrNull() ?: throw IllegalStateException("Rider location not found")
        }
    }

    fun getAvailableDeliveries(riderId: UUID): List<AvailableDelivery> {
        return transaction {
            // Get rider's current location
            val riderLocation = getRiderLocation(riderId)
            
            // Get IDs of orders this rider has rejected
            val rejectedOrderIds = RiderRejectionsTable
                .select { RiderRejectionsTable.riderId eq riderId }
                .map { it[RiderRejectionsTable.orderId] }
                .toSet()
                
            // Find orders that are ready and haven't been assigned to riders
            // and haven't been rejected by this rider
            val query = (OrdersTable
                .join(RestaurantsTable, JoinType.INNER, OrdersTable.restaurantId, RestaurantsTable.id)
                .select {
                    (OrdersTable.status eq OrderStatus.READY.name) and
                    (OrdersTable.riderId.isNull())
                })
                
            query.mapNotNull { row ->
                val orderId = row[OrdersTable.id]
                
                // Skip if rider has already rejected this order
                if (orderId in rejectedOrderIds) {
                    return@mapNotNull null
                }
                
                val restaurantLat = row[RestaurantsTable.latitude]
                val restaurantLng = row[RestaurantsTable.longitude]
                
                // Calculate distance between rider and restaurant
                val distance = calculateDistance(
                    riderLocation.latitude, riderLocation.longitude,
                    restaurantLat, restaurantLng
                )

                // Only show deliveries within reasonable distance (e.g., 5km)
                if (distance <= SEARCH_RADIUS_KM) {
                    // Get customer address
                    val customerAddress = getOrderAddress(row[OrdersTable.addressId])
                    
                    AvailableDelivery(
                        orderId = orderId.toString(),
                        restaurantName = row[RestaurantsTable.name],
                        restaurantAddress = row[RestaurantsTable.address],
                        customerAddress = customerAddress?.addressLine1 ?: "",
                        orderAmount = row[OrdersTable.totalAmount],
                        estimatedDistance = distance,
                        estimatedEarning = calculateEarnings(distance, row[OrdersTable.totalAmount]),
                        createdAt = row[OrdersTable.createdAt].toString()
                    )
                } else null
            }
        }
    }

    fun updateDeliveryStatus(
        riderId: UUID,
        orderId: UUID,
        statusUpdate: DeliveryStatusUpdate
    ): Boolean {
        return transaction {
            // Verify rider is assigned to this order
            val order = OrdersTable
                .select { 
                    (OrdersTable.id eq orderId) and
                    (OrdersTable.riderId eq riderId)
                }
                .firstOrNull() ?: throw IllegalStateException("Order not found or unauthorized")

            // Update order status
            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[status] = when (statusUpdate.status) {
                    "PICKED_UP" -> OrderStatus.OUT_FOR_DELIVERY.name
                    "DELIVERED" -> OrderStatus.DELIVERED.name
                    "FAILED" -> OrderStatus.DELIVERY_FAILED.name
                    else -> throw IllegalArgumentException("Invalid status: ${statusUpdate.status}")
                }
            } > 0

            if (updated) {
                // Notify customer
                val customerId = order[OrdersTable.userId]
                val message = when (statusUpdate.status) {
                    "PICKED_UP" -> "Your order has been picked up and is on the way"
                    "DELIVERED" -> "Your order has been delivered"
                    "FAILED" -> "Delivery failed: ${statusUpdate.reason}"
                    else -> throw IllegalArgumentException("Invalid status")
                }

                NotificationService.createNotification(
                    userId = customerId,
                    title = "Delivery Update",
                    message = message,
                    type = "DELIVERY_STATUS",
                    orderId = orderId
                )
            }

            updated
        }
    }

    private fun calculateEarnings(distance: Double, orderAmount: Double): Double {
        // Basic earnings calculation
        val baseRate = 2.0 // Base rate in dollars
        val perKmRate = 0.5 // Rate per kilometer
        val orderPercentage = 0.05 // 5% of order amount
        
        return baseRate + (distance * perKmRate) + (orderAmount * orderPercentage)
    }

    fun getActiveDeliveries(riderId: UUID): List<RiderDelivery> {
        return transaction {
            // Get orders assigned to this rider that are in active delivery states
            (OrdersTable
                .join(RestaurantsTable, JoinType.INNER, OrdersTable.restaurantId, RestaurantsTable.id)
                .select {
                    (OrdersTable.riderId eq riderId) and
                    (OrdersTable.status inList listOf(
                        OrderStatus.ASSIGNED.name,
                        OrderStatus.OUT_FOR_DELIVERY.name
                    ))
                })
                .orderBy(OrdersTable.updatedAt, SortOrder.DESC)
                .map { row ->
                    val orderId = row[OrdersTable.id]
                    val customerAddress = getOrderAddress(row[OrdersTable.addressId])
                    
                    // Get order items
                    val items = OrderItemsTable
                        .join(MenuItemsTable, JoinType.INNER, OrderItemsTable.menuItemId, MenuItemsTable.id)
                        .select { OrderItemsTable.orderId eq orderId }
                        .map { itemRow ->
                            OrderItemDetail(
                                id = itemRow[OrderItemsTable.id].toString(),
                                name = itemRow[MenuItemsTable.name],
                                quantity = itemRow[OrderItemsTable.quantity],
                                price = itemRow[MenuItemsTable.price]
                            )
                        }
                    
                    RiderDelivery(
                        orderId = orderId.toString(),
                        status = row[OrdersTable.status],
                        restaurant = RestaurantDetail(
                            id = row[RestaurantsTable.id].toString(),
                            name = row[RestaurantsTable.name],
                            address = row[RestaurantsTable.address],
                            latitude = row[RestaurantsTable.latitude],
                            longitude = row[RestaurantsTable.longitude],
                            imageUrl = row[RestaurantsTable.imageUrl] ?: ""
                        ),
                        customer = CustomerAddress(
                            addressLine1 = customerAddress?.addressLine1 ?: "",
                            addressLine2 = customerAddress?.addressLine2,
                            city = customerAddress?.city ?: "",
                            state = customerAddress?.state,
                            zipCode = customerAddress?.zipCode ?: "",
                            latitude = customerAddress?.latitude ?: 0.0,
                            longitude = customerAddress?.longitude ?: 0.0
                        ),
                        items = items,
                        totalAmount = row[OrdersTable.totalAmount],
                        estimatedEarning = calculateEarnings(
                            calculateDistance(
                                row[RestaurantsTable.latitude], row[RestaurantsTable.longitude],
                                customerAddress?.latitude ?: 0.0, customerAddress?.longitude ?: 0.0
                            ),
                            row[OrdersTable.totalAmount]
                        ),
                        createdAt = row[OrdersTable.createdAt].toString(),
                        updatedAt = row[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }
}