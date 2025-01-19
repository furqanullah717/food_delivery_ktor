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
import com.stripe.model.EphemeralKey
import com.stripe.model.Customer
import com.codewithfk.model.PaymentSheetResponse
import com.stripe.net.RequestOptions

object PaymentService {
    init {
        Stripe.apiKey = StripeConfig.secretKey
    }

    fun createPaymentIntent(userId: UUID, addressId: UUID): PaymentIntentResponse {
        try {
            // Get cart total
            val checkoutDetails = OrderService.getCheckoutDetails(userId)
            val amountInCents = (checkoutDetails.totalAmount * 100).toLong()

            // Get or create customer
            val customer = getOrCreateCustomer(userId)

            // Create ephemeral key
            val requestOptions = RequestOptions.builder()
                .build()

            val ephemeralKey = EphemeralKey.create(
                mapOf(
                    "customer" to customer,
                    "stripe-version" to "2020-08-27"  // API version for Stripe Android SDK 20.35.0
                ),
                requestOptions
            )

            // Create payment intent
            val paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setCustomer(customer)
                .putMetadata("userId", userId.toString())
                .putMetadata("addressId", addressId.toString())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )

            val paymentIntent = PaymentIntent.create(paramsBuilder.build())

            return PaymentIntentResponse(
                paymentIntentClientSecret = paymentIntent.clientSecret,
                paymentIntentId = paymentIntent.id,
                customerId = customer,
                ephemeralKeySecret = ephemeralKey.secret,
                publishableKey = StripeConfig.publishableKey,
                amount = amountInCents,
                currency = "usd",
                status = paymentIntent.status
            )
        } catch (e: Exception) {
            throw IllegalStateException("Error creating payment intent: ${e.message}")
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
        println("Payment failed for intent: ${paymentIntent.id}")
        println("Failure message: ${paymentIntent.lastPaymentError?.message}")
    }

    fun createPaymentSheet(userId: UUID, addressId: UUID): PaymentSheetResponse {
        try {
            // Get cart total
            val checkoutDetails = OrderService.getCheckoutDetails(userId)
            val amountInCents = (checkoutDetails.totalAmount * 100).toLong()

            // Get or create customer
            val customer = getOrCreateCustomer(userId)

            // Create ephemeral key with proper request options
            val requestOptions = RequestOptions.builder()
                .build()

            val ephemeralKey = EphemeralKey.create(
                mapOf(
                    "customer" to customer,
                    "stripe-version" to "2020-08-27"  // API version for Stripe Android SDK 20.35.0
                ),
                requestOptions
            )

            // Create payment intent
            val paymentIntent = PaymentIntent.create(
                PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setCustomer(customer)
                    .putMetadata("userId", userId.toString())
                    .putMetadata("addressId", addressId.toString())
                    .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build()
                    )
                    .build()
            )

            return PaymentSheetResponse(
                paymentIntent = paymentIntent.clientSecret,
                ephemeralKey = ephemeralKey.secret,
                customer = customer,
                publishableKey = StripeConfig.publishableKey
            )
        } catch (e: Exception) {
            throw IllegalStateException("Error creating payment sheet: ${e.message}")
        }
    }

    private fun getOrCreateCustomer(userId: UUID): String {
        val user = AuthService.getUserEmailFromID(userId)
            ?: throw IllegalStateException("User not found")

        val existingCustomers = Customer.list(
            mapOf("email" to user)
        )

        if (existingCustomers.data.isNotEmpty()) {
            return existingCustomers.data[0].id
        }

        val customer = Customer.create(
            mapOf(
                "metadata" to mapOf("userId" to userId.toString()),
                "email" to user,
            )
        )

        return customer.id
    }

    fun verifyAndGetPaymentIntent(userId: UUID, paymentIntentId: String): PaymentIntent {
        val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
        
        // Verify ownership
        if (paymentIntent.metadata["userId"] != userId.toString()) {
            throw IllegalStateException("Payment intent does not belong to this user")
        }

        return paymentIntent
    }
} 