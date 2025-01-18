package com.codewithfk.controllers

import com.codewithfk.model.*
import com.codewithfk.services.PaymentService
import com.codewithfk.services.OrderService
import java.util.*

class PaymentController {
    companion object {
        fun createPaymentSession(userId: UUID, request: CreatePaymentIntentRequest): PaymentIntentResponse {
            return PaymentService.createPaymentIntent(
                userId = userId,
                addressId = UUID.fromString(request.addressId),
                paymentMethodId = request.paymentMethodId
            )
        }

        fun confirmPaymentAndPlaceOrder(userId: UUID, request: ConfirmPaymentRequest): PaymentConfirmationResponse {
            val paymentIntent = PaymentService.confirmPayment(
                userId = userId,
                paymentIntentId = request.paymentIntentId,
                paymentMethodId = request.paymentMethodId
            )

            // If payment succeeded, create order
            return if (paymentIntent.status == "succeeded") {
                val orderId = OrderService.placeOrder(
                    userId = userId,
                    request = PlaceOrderRequest(addressId = request.addressId),
                    paymentIntentId = paymentIntent.id
                )

                PaymentConfirmationResponse(
                    status = paymentIntent.status,
                    requiresAction = false,
                    clientSecret = paymentIntent.clientSecret,
                    orderId = orderId.toString(),
                    orderStatus = "Pending",
                    message = "Payment successful and order placed"
                )
            } else {
                PaymentConfirmationResponse(
                    status = paymentIntent.status,
                    requiresAction = paymentIntent.status == "requires_action",
                    clientSecret = paymentIntent.clientSecret,
                    message = when(paymentIntent.status) {
                        "requires_action" -> "Additional authentication required"
                        "requires_payment_method" -> "Payment failed, please try another payment method"
                        else -> "Payment processing"
                    }
                )
            }
        }

        fun handleWebhookEvent(payload: String, signature: String): Boolean {
            return PaymentService.handleWebhook(payload, signature)
        }
    }
} 