package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class Address(
    val id: String? = null,
    val userId: String? = null,
    val addressLine1: String,
    val addressLine2: String? = null,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class ReverseGeocodeRequest(
    @Serializable
    val latitude: Double,
    @Serializable
    val longitude: Double
) {
    init {
        require(latitude >= -90 && latitude <= 90) { "Latitude must be between -90 and 90" }
        require(longitude >= -180 && longitude <= 180) { "Longitude must be between -180 and 180" }
    }
} 