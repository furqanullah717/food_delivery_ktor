package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationUpdate(
    val type: String = "LOCATION_UPDATE",
    val riderId: String,
    val orderId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TrackingSession(
    val type: String = "TRACKING_SESSION",
    val orderId: String,
    val riderId: String,
    val deliveryPhase: DeliveryPhase
) 