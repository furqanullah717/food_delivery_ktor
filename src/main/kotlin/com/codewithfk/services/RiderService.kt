package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import com.google.maps.DirectionsApi
import com.google.maps.model.TravelMode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.*

object RiderService {
    private const val SEARCH_RADIUS_KM = 5.0
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
            try {
                // Accept the request
                val accepted = DeliveryRequestsTable.update({ 
                    (DeliveryRequestsTable.orderId eq orderId) and
                    (DeliveryRequestsTable.riderId eq riderId) and
                    (DeliveryRequestsTable.status eq "PENDING")
                }) {
                    it[status] = "ACCEPTED"
                } > 0

                if (accepted) {
                    // Cancel other requests
                    DeliveryRequestsTable.update({
                        (DeliveryRequestsTable.orderId eq orderId) and
                        (DeliveryRequestsTable.riderId neq riderId)
                    }) {
                        it[status] = "CANCELLED"
                    }

                    // Update order with rider ID
                    OrdersTable.update({ OrdersTable.id eq orderId }) {
                        it[OrdersTable.riderId] = riderId
                        it[status] = OrderStatus.ACCEPTED.name
                    }

                    // Notify stakeholders
                    notifyOrderAccepted(orderId)
                }
                accepted
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun notifyOrderAccepted(orderId: UUID) {
        val order = OrderService.getOrderDetails(orderId)
        
        // Notify customer
        NotificationService.createNotification(
            userId = UUID.fromString(order.userId),
            title = "Rider Assigned",
            message = "A rider has been assigned to your order",
            type = "ORDER_UPDATE",
            orderId = orderId
        )

        // Notify restaurant
        val restaurantOwnerId = transaction {
            RestaurantsTable
                .select { RestaurantsTable.id eq UUID.fromString(order.restaurantId) }
                .map { it[RestaurantsTable.ownerId] }
                .single()
        }

        NotificationService.createNotification(
            userId = restaurantOwnerId,
            title = "Rider Assigned",
            message = "A rider has been assigned to order #${orderId.toString().take(8)}",
            type = "ORDER_UPDATE",
            orderId = orderId
        )
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
} 