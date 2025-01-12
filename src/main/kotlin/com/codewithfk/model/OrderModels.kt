package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceOrderRequest(
    val addressId: String
)

@Serializable
data class Order(
    val id: String,
    val userId: String,
    val restaurantId: String,
    val address: Address?,
    val status: String,
    val paymentStatus: String,
    val stripePaymentIntentId: String?,
    val totalAmount: Double,
    val items: List<OrderItem>? = null,
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