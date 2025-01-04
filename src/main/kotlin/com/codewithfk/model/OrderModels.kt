package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val userId: String,
    val restaurantId: String,
    val addressId: String,
    val status: String, // Pending, Preparing, Picked Up, Delivered
    val paymentStatus: String, // Pending, Paid, Failed
    val stripePaymentIntentId: String?, // Stripe Payment Intent ID
    val totalAmount: Double,
    val items: List<OrderItem>? = null, // List of items in the order
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderItem(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val quantity: Int
)

@Serializable
data class AddToCartRequest(
    val restaurantId: String,
    val menuItemId: String,
    val quantity: Int
)