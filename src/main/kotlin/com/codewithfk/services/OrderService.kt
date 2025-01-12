package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.CheckoutModel
import com.codewithfk.model.Order
import com.codewithfk.model.PlaceOrderRequest
import com.codewithfk.model.OrderItem
import com.codewithfk.model.Address
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
                it[this.status] = "Pending"
                it[this.paymentStatus] = if (paymentIntentId != null) "Paid" else "Pending"
                it[this.stripePaymentIntentId] = paymentIntentId
            } get OrdersTable.id

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
            OrdersTable.select { OrdersTable.userId eq userId }
                .map { orderRow ->
                    val orderId = orderRow[OrdersTable.id]
                    
                    // Get address
                    val address = AddressesTable.select {
                        AddressesTable.id eq orderRow[OrdersTable.addressId] 
                    }.map { addressRow ->
                        Address(
                            id = addressRow[AddressesTable.id].toString(),
                            userId = addressRow[AddressesTable.userId].toString(),
                            addressLine1 = addressRow[AddressesTable.addressLine1],
                            addressLine2 = addressRow[AddressesTable.addressLine2],
                            city = addressRow[AddressesTable.city],
                            state = addressRow[AddressesTable.state],
                            zipCode = addressRow[AddressesTable.zipCode],
                            country = addressRow[AddressesTable.country],
                            latitude = addressRow[AddressesTable.latitude],
                            longitude = addressRow[AddressesTable.longitude]
                        )
                    }.singleOrNull()

                    // Get order items
                    val items = OrderItemsTable.select { 
                        OrderItemsTable.orderId eq orderId 
                    }.map { itemRow ->
                        OrderItem(
                            id = itemRow[OrderItemsTable.id].toString(),
                            orderId = orderId.toString(),
                            menuItemId = itemRow[OrderItemsTable.menuItemId].toString(),
                            quantity = itemRow[OrderItemsTable.quantity]
                        )
                    }

                    Order(
                        id = orderId.toString(),
                        userId = orderRow[OrdersTable.userId].toString(),
                        restaurantId = orderRow[OrdersTable.restaurantId].toString(),
                        address = address,
                        status = orderRow[OrdersTable.status],
                        paymentStatus = orderRow[OrdersTable.paymentStatus],
                        stripePaymentIntentId = orderRow[OrdersTable.stripePaymentIntentId],
                        totalAmount = orderRow[OrdersTable.totalAmount],
                        items = items,
                        createdAt = orderRow[OrdersTable.createdAt].toString(),
                        updatedAt = orderRow[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }

    fun getOrderDetails(orderId: UUID): Order {
        return transaction {
            val orderRow = OrdersTable.select { OrdersTable.id eq orderId }.singleOrNull()
                ?: throw IllegalStateException("Order not found")

            // Get address
            val address = AddressesTable.select { 
                AddressesTable.id eq orderRow[OrdersTable.addressId] 
            }.map { addressRow ->
                Address(
                    id = addressRow[AddressesTable.id].toString(),
                    userId = addressRow[AddressesTable.userId].toString(),
                    addressLine1 = addressRow[AddressesTable.addressLine1],
                    addressLine2 = addressRow[AddressesTable.addressLine2],
                    city = addressRow[AddressesTable.city],
                    state = addressRow[AddressesTable.state],
                    zipCode = addressRow[AddressesTable.zipCode],
                    country = addressRow[AddressesTable.country],
                    latitude = addressRow[AddressesTable.latitude],
                    longitude = addressRow[AddressesTable.longitude]
                )
            }.singleOrNull()

            // Get order items
            val items = OrderItemsTable.select { 
                OrderItemsTable.orderId eq orderId 
            }.map { itemRow ->
                OrderItem(
                    id = itemRow[OrderItemsTable.id].toString(),
                    orderId = orderId.toString(),
                    menuItemId = itemRow[OrderItemsTable.menuItemId].toString(),
                    quantity = itemRow[OrderItemsTable.quantity]
                )
            }

            Order(
                id = orderRow[OrdersTable.id].toString(),
                userId = orderRow[OrdersTable.userId].toString(),
                restaurantId = orderRow[OrdersTable.restaurantId].toString(),
                address = address,
                status = orderRow[OrdersTable.status],
                paymentStatus = orderRow[OrdersTable.paymentStatus],
                stripePaymentIntentId = orderRow[OrdersTable.stripePaymentIntentId],
                totalAmount = orderRow[OrdersTable.totalAmount],
                items = items,
                createdAt = orderRow[OrdersTable.createdAt].toString(),
                updatedAt = orderRow[OrdersTable.updatedAt].toString()
            )
        }
    }

    fun updateOrderStatus(orderId: UUID, status: String): Boolean {
        return transaction {
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.status] = status
                it[OrdersTable.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } > 0
        }
    }
}