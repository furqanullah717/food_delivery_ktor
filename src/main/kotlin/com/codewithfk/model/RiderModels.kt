package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class RiderLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val isAvailable: Boolean,
    val lastUpdated: String
)

@Serializable
data class DeliveryRequest(
    val orderId: String,
    val restaurantLocation: Location,
    val customerLocation: Location,
    val estimatedEarning: Double,
    val distance: Double,
    val status: String // PENDING, ACCEPTED, REJECTED
)

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

@Serializable
data class DeliveryPath(
    val currentLocation: Location,
    val nextStop: Location,
    val finalDestination: Location,
    val polyline: String, // Encoded polyline from Google Maps
    val estimatedTime: Int, // in minutes
    val deliveryPhase: DeliveryPhase
)

enum class DeliveryPhase {
    TO_RESTAURANT,    // Rider heading to restaurant
    TO_CUSTOMER      // Rider heading to customer
} 