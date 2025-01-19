package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentIntentRequest(
    val addressId: String,
    val paymentMethodId: String? = null
)

@Serializable
data class PaymentIntentResponse(
    val paymentIntentClientSecret: String,
    val paymentIntentId: String,
    val customerId: String,
    val ephemeralKeySecret: String,
    val publishableKey: String,
    val amount: Long,
    val currency: String = "usd",
    val status: String
)

// For webhook handling
@Serializable
data class PaymentWebhookResponse(
    val success: Boolean,
    val orderId: String? = null,
    val message: String? = null
)

@Serializable
data class ConfirmPaymentRequest(
    val paymentIntentId: String,
    val addressId: String,
    val paymentMethodId: String? = null
)

@Serializable
data class PaymentMethodRequest(
    val type: String,
    val card: CardDetails? = null
)

@Serializable
data class CardDetails(
    val number: String,
    val expMonth: Int,
    val expYear: Int,
    val cvc: String
)

@Serializable
data class PaymentConfirmationResponse(
    val status: String,
    val requiresAction: Boolean,
    val clientSecret: String,
    val orderId: String? = null,
    val orderStatus: String? = null,
    val message: String? = null
)

@Serializable
data class PaymentSheetResponse(
    val paymentIntent: String,
    val ephemeralKey: String,
    val customer: String,
    val publishableKey: String
) 