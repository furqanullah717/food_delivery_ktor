package com.codewithfk.services

import com.codewithfk.database.CartTable
import com.codewithfk.database.MenuItemsTable
import com.codewithfk.database.OrderItemsTable
import com.codewithfk.database.OrdersTable
import com.codewithfk.model.CheckoutModel
import com.codewithfk.model.Order
import com.codewithfk.utils.StripeUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
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

    fun placeOrder(userId: UUID, restaurantId: UUID, addressId: UUID): UUID {
        return transaction {
            val cartItems =
                CartTable.select { (CartTable.userId eq userId) and (CartTable.restaurantId eq restaurantId) }

            if (cartItems.empty()) {
                throw IllegalStateException("Cart is empty")
            }

            // Calculate total amount
            val totalAmount = cartItems.sumOf {
                val quantity = it[CartTable.quantity]
                val price = MenuItemsTable.select { MenuItemsTable.id eq it[CartTable.menuItemId] }
                    .single()[MenuItemsTable.price]
                quantity * price
            }

            // Create Stripe payment intent
            val stripePaymentIntentId = StripeUtils.createPaymentIntent((totalAmount * 100).toLong())

            // Create a new order
            val orderId = OrdersTable.insert {
                it[this.userId] = userId
                it[this.restaurantId] = restaurantId
                it[this.addressId] = addressId
                it[this.totalAmount] = totalAmount
                it[this.stripePaymentIntentId] = stripePaymentIntentId
            } get OrdersTable.id

            // Add cart items to the order
            cartItems.forEach { cartItem ->
                OrderItemsTable.insert {
                    it[this.orderId] = orderId
                    it[this.menuItemId] = cartItem[CartTable.menuItemId]
                    it[this.quantity] = cartItem[CartTable.quantity]
                }
            }

            // Clear the cart
            CartTable.deleteWhere { (CartTable.userId eq userId) and (CartTable.restaurantId eq restaurantId) }

            orderId
        }
    }

    fun getOrdersByUser(userId: UUID): List<Order> {
        return transaction {
            OrdersTable.select { OrdersTable.userId eq userId }
                .map {
                    Order(
                        id = it[OrdersTable.id].toString(),
                        userId = it[OrdersTable.userId].toString(),
                        restaurantId = it[OrdersTable.restaurantId].toString(),
                        addressId = it[OrdersTable.addressId].toString(),
                        status = it[OrdersTable.status],
                        paymentStatus = it[OrdersTable.paymentStatus],
                        stripePaymentIntentId = it[OrdersTable.stripePaymentIntentId],
                        totalAmount = it[OrdersTable.totalAmount],
                        createdAt = it[OrdersTable.createdAt].toString(),
                        updatedAt = it[OrdersTable.updatedAt].toString()
                    )
                }
        }
    }

    fun getOrderDetails(orderId: UUID): Order {
        return transaction {
            val order = OrdersTable.select { OrdersTable.id eq orderId }.singleOrNull()
                ?: throw IllegalStateException("Order not found")

//            val orderItems = OrderItemsTable.select { OrderItemsTable.orderId eq orderId }
//                .map {
//                    OrderItem(
//                        id = it[OrderItemsTable.id].toString(),
//                        orderId = it[OrderItemsTable.orderId].toString(),
//                        menuItemId = it[OrderItemsTable.menuItemId].toString(),
//                        quantity = it[OrderItemsTable.quantity]
//                    )
//                }

            Order(
                id = order[OrdersTable.id].toString(),
                userId = order[OrdersTable.userId].toString(),
                restaurantId = order[OrdersTable.restaurantId].toString(),
                addressId = order[OrdersTable.addressId].toString(),
                status = order[OrdersTable.status],
                paymentStatus = order[OrdersTable.paymentStatus],
                stripePaymentIntentId = order[OrdersTable.stripePaymentIntentId],
                totalAmount = order[OrdersTable.totalAmount],
                createdAt = order[OrdersTable.createdAt].toString(),
                updatedAt = order[OrdersTable.updatedAt].toString(),
                items = emptyList()
            )
        }
    }

    fun updateOrderStatus(orderId: UUID, status: String): Boolean {
        return false
//        return transaction {
//            OrdersTable.update({ OrdersTable.id eq orderId }) {
//                it[this.status] = status
//                it[this.updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp
//            } > 0
//        }
    }
}