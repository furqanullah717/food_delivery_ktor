package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckoutModel(
    val subTotal: Double,
    val totalAmount: Double,
    val tax: Double,
    val deliveryFee: Double
)