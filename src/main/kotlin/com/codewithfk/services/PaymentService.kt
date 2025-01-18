package com.codewithfk.services

import com.codewithfk.configs.StripeConfig
import com.codewithfk.model.PaymentIntentResponse
import com.codewithfk.model.PlaceOrderRequest
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.model.Event
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.PaymentIntentConfirmParams
import java.util.*

object PaymentService {
    init {
        Stripe.apiKey = StripeConfig.secretKey
    }

    fun createPaymentIntent(userId: UUID, addressId: UUID, paymentMethodId: String? = null): PaymentIntentResponse {
        try {
            // Get cart total
            val checkoutDetails = OrderService.getCheckoutDetails(userId)
            val amountInCents = (checkoutDetails.totalAmount * 100).toLong()

            val paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .putMetadata("userId", userId.toString())
                .putMetadata("addressId", addressId.toString())
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)

            // If payment method is provided, attach it
            if (paymentMethodId != null) {
                paramsBuilder
                    .setPaymentMethod(paymentMethodId)
                    .setConfirm(true)
                    .setOffSession(false)
            }

            val paymentIntent = PaymentIntent.create(paramsBuilder.build())

            return PaymentIntentResponse(
                clientSecret = paymentIntent.clientSecret,
                paymentIntentId = paymentIntent.id,
                amount = amountInCents,
                currency = "usd",
                status = paymentIntent.status,
                requiresAction = paymentIntent.status == "requires_action",
                paymentMethodId = paymentIntent.paymentMethod
            )
        } catch (e: Exception) {
            throw IllegalStateException("Error creating payment intent: ${e.message}")
        }
    }

    fun confirmPayment(userId: UUID, paymentIntentId: String, paymentMethodId: String? = null): PaymentIntent {
        try {
            val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
            
            // Verify ownership
            if (paymentIntent.metadata["userId"] != userId.toString()) {
                throw IllegalStateException("Payment intent does not belong to this user")
            }

            // If new payment method provided, confirm with it
            if (paymentMethodId != null) {
                val params = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod(paymentMethodId)
                    .setReturnUrl("https://your-domain.com/payment/complete") // Add return URL
                    .build()
                return paymentIntent.confirm(params)
            }

            return when (paymentIntent.status) {
                "requires_payment_method" -> throw IllegalStateException("Payment method required")
                "requires_action" -> throw IllegalStateException("Additional authentication required")
                "requires_confirmation" -> paymentIntent.confirm()
                "succeeded" -> paymentIntent
                else -> throw IllegalStateException("Invalid payment status: ${paymentIntent.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error confirming payment: ${e.message}")
        }
    }

    fun handleWebhook(payload: String, sigHeader: String): Boolean {
        try {
            val event = com.stripe.net.Webhook.constructEvent(
                payload,
                sigHeader,
                StripeConfig.webhookSecret
            )

            when (event.type) {
                "payment_intent.succeeded" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.get() as PaymentIntent
                    handleSuccessfulPayment(paymentIntent)
                }
                "payment_intent.payment_failed" -> {
                    val paymentIntent = event.dataObjectDeserializer.`object`.get() as PaymentIntent
                    handleFailedPayment(paymentIntent)
                }
            }
            return true
        } catch (e: Exception) {
            throw IllegalStateException("Webhook handling failed: ${e.message}")
        }
    }

    private fun handleSuccessfulPayment(paymentIntent: PaymentIntent) {
        try {
            val userId = UUID.fromString(paymentIntent.metadata["userId"])
            val addressId = UUID.fromString(paymentIntent.metadata["addressId"])
            
            OrderService.placeOrder(
                userId = userId,
                request = PlaceOrderRequest(addressId = addressId.toString()),
                paymentIntentId = paymentIntent.id
            )
        } catch (e: Exception) {
            throw IllegalStateException("Error handling successful payment: ${e.message}")
        }
    }

    private fun handleFailedPayment(paymentIntent: PaymentIntent) {
        // Log the failure
        println("Payment failed for intent: ${paymentIntent.id}")
        println("Failure message: ${paymentIntent.lastPaymentError?.message}")
    }

    // Optional: Implement customer management if needed
    private fun getOrCreateCustomer(userId: UUID): String {
        // TODO: Implement customer management
        return "cus_xxx"
    }
} 