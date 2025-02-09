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
    val riderId: String?,
    val address: Address?,
    val status: String,
    val paymentStatus: String,
    val stripePaymentIntentId: String?,
    val totalAmount: Double,
    val items: List<OrderItem>? = null,
    val restaurant: Restaurant? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrderItem(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val quantity: Int,
    val menuItemName:String?
)

@Serializable
data class AddToCartRequest(
    val restaurantId: String,
    val menuItemId: String,
    val quantity: Int
)

// Add these order statuses as an enum
enum class OrderStatus {
    PENDING_ACCEPTANCE, // Initial state when order is placed
    ACCEPTED,          // Restaurant accepted the order
    PREPARING,         // Food is being prepared
    READY,            // Ready for delivery/pickup
    OUT_FOR_DELIVERY, // Rider picked up
    DELIVERED,        // Order completed
    REJECTED,         // Restaurant rejected the order
    CANCELLED         // Customer cancelled
}

// Add order action request model
@Serializable
data class OrderActionRequest(
    val action: String, // "ACCEPT", "REJECT"
    val reason: String? = null
)