package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentIntentRequest(
    val addressId: String
)

@Serializable
data class PaymentIntentResponse(
    val clientSecret: String,
    val paymentIntentId: String,
    val amount: Long,
    val currency: String = "usd"
)

@Serializable
data class ConfirmPaymentRequest(
    val paymentIntentId: String,
    val addressId: String
) 