package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import com.codewithfk.utils.StripeUtils
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object OrderService {

    fun getCheckoutDetails(userId: UUID): CheckoutModel {
        return transaction {
            val cartItems =
                CartTable.select { (CartTable.userId eq userId) }

            if (cartItems.empty()) {
                return@transaction CheckoutModel(
                    subTotal = 0.0,
                    totalAmount = 0.0,
                    tax = 0.0,
                    deliveryFee = 0.0
                )
            }

            val totalAmount = cartItems.sumOf {
                val quantity = it[CartTable.quantity]
                val price = MenuItemsTable.select { MenuItemsTable.id eq it[CartTable.menuItemId] }
                    .single()[MenuItemsTable.price]
                quantity * price
            }

            val tax = totalAmount * 0.1
            val deliveryFee = 1.0
            val total = totalAmount + tax + deliveryFee

            CheckoutModel(
                subTotal = totalAmount,
                totalAmount = total,
                tax = tax,
                deliveryFee = deliveryFee
            )
        }
    }

    fun placeOrder(userId: UUID, request: PlaceOrderRequest, paymentIntentId: String? = null): UUID {
        return transaction {
            // Verify address belongs to user
            val address = AddressService.getAddressById(UUID.fromString(request.addressId))
                ?: throw IllegalStateException("Address not found")

            if (address.userId != userId.toString()) {
                throw IllegalStateException("Address does not belong to user")
            }

            // Get cart items
            val cartItems = CartTable.select { CartTable.userId eq userId }

            if (cartItems.empty()) {
                throw IllegalStateException("Cart is empty")
            }

            // Verify all items are from the same restaurant
            val restaurantId = cartItems.first()[CartTable.restaurantId]
            val allSameRestaurant = cartItems.all { it[CartTable.restaurantId] == restaurantId }
            if (!allSameRestaurant) {
                throw IllegalStateException("All items must be from the same restaurant")
            }

            // Calculate total amount
            val totalAmount = cartItems.sumOf {
                val quantity = it[CartTable.quantity]
                val price = MenuItemsTable.select { MenuItemsTable.id eq it[CartTable.menuItemId] }
                    .single()[MenuItemsTable.price]
                quantity * price
            }

            // Create order
            val orderId = OrdersTable.insert {
                it[this.userId] = userId
                it[this.restaurantId] = restaurantId
                it[this.addressId] = UUID.fromString(request.addressId)
                it[this.totalAmount] = totalAmount
                it[this.status] = OrderStatus.PENDING_ACCEPTANCE.name
                it[this.paymentStatus] = if (paymentIntentId != null) "Paid" else "Pending"
                it[this.stripePaymentIntentId] = paymentIntentId
                it[this.riderId] = null
            } get OrdersTable.id

            // Get restaurant owner's ID
            val restaurantOwnerId = RestaurantsTable
                .select { RestaurantsTable.id eq restaurantId }
                .map { it[RestaurantsTable.ownerId] }
                .single()

            // Send notification to restaurant owner
            NotificationService.createNotification(
                userId = restaurantOwnerId,
                title = "New Order Received",
                message = "New order #${orderId.toString().take(8)} worth $${totalAmount} is waiting for acceptance",
                type = "order",
                orderId = orderId
            )

            // Create order items
            cartItems.forEach { cartItem ->
                OrderItemsTable.insert {
                    it[this.orderId] = orderId
                    it[this.menuItemId] = cartItem[CartTable.menuItemId]
                    it[this.quantity] = cartItem[CartTable.quantity]
                }
            }

            // Clear cart
            CartTable.deleteWhere { CartTable.userId eq userId }

            orderId
        }
    }

    fun getOrdersByUser(userId: UUID): List<Order> {
        return transaction {
            (OrdersTable
                .join(RestaurantsTable, JoinType.LEFT, OrdersTable.restaurantId, RestaurantsTable.id)
                .select { OrdersTable.userId eq userId })
                .map { orderRow ->
                    val orderId = orderRow[OrdersTable.id]
                    
                    // Get address
                    val address = getOrderAddress(orderRow[OrdersTable.addressId])
                    
                    // Get order items
                    val items = getOrderItems(orderId)

                    Order(
                        id = orderId.toString(),
                        userId = orderRow[OrdersTable.userId].toString(),
                        restaurantId = orderRow[OrdersTable.restaurantId].toString(),
                        riderId = orderRow[OrdersTable.riderId]?.toString(),
                        address = address,
                        status = orderRow[OrdersTable.status],
                        paymentStatus = orderRow[OrdersTable.paymentStatus],
                        stripePaymentIntentId = orderRow[OrdersTable.stripePaymentIntentId],
                        totalAmount = orderRow[OrdersTable.totalAmount],
                        items = items,
                        restaurant = Restaurant(
                            id = orderRow[RestaurantsTable.id].toString(),
                            ownerId = orderRow[RestaurantsTable.ownerId].toString(),
                            name = orderRow[RestaurantsTable.name],
                            address = orderRow[RestaurantsTable.address],
                            categoryId = orderRow[RestaurantsTable.categoryId].toString(),
                            latitude = orderRow[RestaurantsTable.latitude],
                            longitude = orderRow[RestaurantsTable.longitude],
                            imageUrl = orderRow[RestaurantsTable.imageUrl] ?: "",
                            createdAt = orderRow[RestaurantsTable.createdAt].toString()
                        ),
                        createdAt = orderRow[OrdersTable.createdAt].toString(),
                        updatedAt = orderRow[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }

    fun getOrderDetails(orderId: UUID): Order {
        return transaction {
            val order = OrdersTable
                .select { OrdersTable.id eq orderId }
                .firstOrNull() ?: throw IllegalStateException("Order not found")

            Order(
                id = order[OrdersTable.id].toString(),
                userId = order[OrdersTable.userId].toString(),
                restaurantId = order[OrdersTable.restaurantId].toString(),
                riderId = order[OrdersTable.riderId]?.toString(),
                address = getOrderAddress(order[OrdersTable.addressId]),
                status = order[OrdersTable.status],
                paymentStatus = order[OrdersTable.paymentStatus],
                stripePaymentIntentId = order[OrdersTable.stripePaymentIntentId],
                totalAmount = order[OrdersTable.totalAmount],
                items = getOrderItems(orderId),
                restaurant = getRestaurantDetails(order[OrdersTable.restaurantId]),
                createdAt = order[OrdersTable.createdAt].toString(),
                updatedAt = order[OrdersTable.updatedAt].toString()
            )
        }
    }

    fun updateOrderStatus(orderId: UUID, status: String): Boolean {
        return transaction {
            val updated = OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.status] = status
                it[OrdersTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } > 0

            if (updated) {
                // Get user ID for the order
                val userId = OrdersTable
                    .select { OrdersTable.id eq orderId }
                    .map { it[OrdersTable.userId] }
                    .single()

                // Create notification
                NotificationService.createNotification(
                    userId = userId,
                    title = "Order Status Updated",
                    message = "Your order #${orderId.toString().take(8)} status has been updated to $status",
                    type = "order",
                    orderId = orderId
                )
            }

            updated
        }
    }

    fun getOrderByPaymentIntentId(paymentIntentId: String): Order? {
        return transaction {
            OrdersTable
                .join(RestaurantsTable, JoinType.LEFT, OrdersTable.restaurantId, RestaurantsTable.id)
                .select { OrdersTable.stripePaymentIntentId eq paymentIntentId }
                .map { row ->
                    Order(
                        id = row[OrdersTable.id].toString(),
                        userId = row[OrdersTable.userId].toString(),
                        restaurantId = row[OrdersTable.restaurantId].toString(),
                        riderId = row[OrdersTable.riderId]?.toString(),
                        status = row[OrdersTable.status],
                        paymentStatus = row[OrdersTable.paymentStatus],
                        stripePaymentIntentId = row[OrdersTable.stripePaymentIntentId],
                        totalAmount = row[OrdersTable.totalAmount],
                        createdAt = row[OrdersTable.createdAt].toString(),
                        updatedAt = row[OrdersTable.updatedAt].toString(),
                        address = getOrderAddress(row[OrdersTable.addressId]),
                        items = getOrderItems(row[OrdersTable.id]),
                        restaurant = Restaurant(
                            id = row[RestaurantsTable.id].toString(),
                            ownerId = row[RestaurantsTable.ownerId].toString(),
                            name = row[RestaurantsTable.name],
                            address = row[RestaurantsTable.address],
                            categoryId = row[RestaurantsTable.categoryId].toString(),
                            latitude = row[RestaurantsTable.latitude],
                            longitude = row[RestaurantsTable.longitude],
                            imageUrl = row[RestaurantsTable.imageUrl] ?: "",
                            createdAt = row[RestaurantsTable.createdAt].toString()
                        )
                    )
                }
                .singleOrNull()
        }
    }

    fun handleOrderAction(orderId: UUID, ownerId: UUID, action: String, reason: String? = null): Boolean {
        return transaction {
            // Verify restaurant ownership
            val order = OrdersTable
                .join(RestaurantsTable, JoinType.INNER, OrdersTable.restaurantId, RestaurantsTable.id)
                .select { 
                    (OrdersTable.id eq orderId) and 
                    (RestaurantsTable.ownerId eq ownerId) 
                }
                .firstOrNull() ?: throw IllegalStateException("Order not found or unauthorized")

            val currentStatus = order[OrdersTable.status]
            if (currentStatus != OrderStatus.PENDING_ACCEPTANCE.name) {
                throw IllegalStateException("Order cannot be ${action.toLowerCase()} in status: $currentStatus")
            }

            val newStatus = when (action.toUpperCase()) {
                "ACCEPT" -> OrderStatus.ACCEPTED
                "REJECT" -> OrderStatus.REJECTED
                else -> throw IllegalArgumentException("Invalid action: $action")
            }

            // Update order status
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[status] = newStatus.name
            }

            // Notify customer
            val customerId = order[OrdersTable.userId]
            val message = when (newStatus) {
                OrderStatus.ACCEPTED -> "Your order has been accepted and will be prepared soon"
                OrderStatus.REJECTED -> "Your order was rejected${reason?.let { " - $it" } ?: ""}"
                else -> throw IllegalStateException("Unexpected status")
            }

            NotificationService.createNotification(
                userId = customerId,
                title = "Order Update",
                message = message,
                type = "ORDER_STATUS",
                orderId = orderId
            )

            // If rejected, initiate refund if payment was made
            if (newStatus == OrderStatus.REJECTED) {
                order[OrdersTable.stripePaymentIntentId]?.let { paymentIntentId ->
                    // Implement refund logic
                    // PaymentService.initiateRefund(paymentIntentId)
                }
            }

            true
        }
    }

    private fun getOrderAddress(addressId: UUID): Address? {
        return AddressesTable
            .select { AddressesTable.id eq addressId }
            .map { row ->
                Address(
                    id = row[AddressesTable.id].toString(),
                    userId = row[AddressesTable.userId].toString(),
                    addressLine1 = row[AddressesTable.addressLine1],
                    addressLine2 = row[AddressesTable.addressLine2],
                    city = row[AddressesTable.city],
                    state = row[AddressesTable.state],
                    zipCode = row[AddressesTable.zipCode],
                    country = row[AddressesTable.country],
                    latitude = row[AddressesTable.latitude],
                    longitude = row[AddressesTable.longitude]
                )
            }
            .singleOrNull()
    }

    private fun getOrderItems(orderId: UUID): List<OrderItem> {
        return OrderItemsTable
            .select { OrderItemsTable.orderId eq orderId }
            .map { row ->
                val item = MenuItemsTable.select({ MenuItemsTable.id eq row[OrderItemsTable.menuItemId] }).single()
                OrderItem(
                    id = row[OrderItemsTable.id].toString(),
                    orderId = orderId.toString(),
                    menuItemId = row[OrderItemsTable.menuItemId].toString(),
                    quantity = row[OrderItemsTable.quantity],
                    menuItemName = item[MenuItemsTable.name]
                )
            }
    }

    private fun getRestaurantDetails(restaurantId: UUID): Restaurant? {
        return RestaurantsTable
            .select { RestaurantsTable.id eq restaurantId }
            .map { row ->
                Restaurant(
                    id = row[RestaurantsTable.id].toString(),
                    ownerId = row[RestaurantsTable.ownerId].toString(),
                    name = row[RestaurantsTable.name],
                    address = row[RestaurantsTable.address],
                    categoryId = row[RestaurantsTable.categoryId].toString(),
                    latitude = row[RestaurantsTable.latitude],
                    longitude = row[RestaurantsTable.longitude],
                    imageUrl = row[RestaurantsTable.imageUrl] ?: "",
                    createdAt = row[RestaurantsTable.createdAt].toString()
                )
            }
            .singleOrNull()
    }
}