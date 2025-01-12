package com.codewithfk.services

import com.codewithfk.model.PaymentIntentResponse
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.param.PaymentIntentCreateParams
import java.util.*

object PaymentService {
    init {
        Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY") // Configure in your environment
    }

    fun createPaymentIntent(userId: UUID, addressId: UUID): PaymentIntentResponse {
        // Get cart total
        val checkoutDetails = OrderService.getCheckoutDetails(userId)
        val amountInCents = (checkoutDetails.totalAmount * 100).toLong() // Convert to cents

        // Create payment intent
        val params = PaymentIntentCreateParams.builder()
            .setAmount(amountInCents)
            .setCurrency("usd")
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build()
            )
            .putMetadata("userId", userId.toString())
            .putMetadata("addressId", addressId.toString())
            .build()

        val paymentIntent = PaymentIntent.create(params)

        return PaymentIntentResponse(
            clientSecret = paymentIntent.clientSecret,
            paymentIntentId = paymentIntent.id,
            amount = amountInCents,
            currency = "usd"
        )
    }

    fun confirmPayment(userId: UUID, paymentIntentId: String): PaymentIntent {
        val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
        
        // Verify this payment intent belongs to this user
        if (paymentIntent.metadata["userId"] != userId.toString()) {
            throw IllegalStateException("Payment intent does not belong to this user")
        }

        // Check payment status
        if (paymentIntent.status != "succeeded") {
            throw IllegalStateException("Payment has not been completed")
        }

        return paymentIntent
    }
} 