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
                addressId = UUID.fromString(request.addressId)
            )
        }

        fun confirmAndPlaceOrder(userId: UUID, paymentIntentId: String): PaymentConfirmationResponse {
            val paymentIntent = PaymentService.verifyAndGetPaymentIntent(
                userId = userId,
                paymentIntentId = paymentIntentId
            )

            return when (paymentIntent.status) {
                "succeeded" -> {
                    try {
                        val addressId = paymentIntent.metadata["addressId"] 
                            ?: throw IllegalStateException("Address ID not found in payment intent")
                        
                        val orderId = OrderService.placeOrder(
                            userId = userId,
                            request = PlaceOrderRequest(addressId = addressId),
                            paymentIntentId = paymentIntent.id
                        )

                        PaymentConfirmationResponse(
                            status = paymentIntent.status,
                            requiresAction = false,
                            clientSecret = paymentIntent.clientSecret,
                            orderId = orderId.toString(),
                            orderStatus = "Pending",
                            message = "Order placed successfully"
                        )
                    } catch (e: IllegalStateException) {
                        // If order already exists, get the existing order ID
                        if (e.message?.contains("Order already exists") == true) {
                            // Get existing order by paymentIntentId
                            val existingOrder = OrderService.getOrderByPaymentIntentId(paymentIntent.id)
                                ?: throw IllegalStateException("Order not found for payment")

                            PaymentConfirmationResponse(
                                status = paymentIntent.status,
                                requiresAction = false,
                                clientSecret = paymentIntent.clientSecret,
                                orderId = existingOrder.id,
                                orderStatus = existingOrder.status,
                                message = "Payment already processed"
                            )
                        } else {
                            throw e
                        }
                    }
                }
                else -> PaymentConfirmationResponse(
                    status = paymentIntent.status,
                    requiresAction = false,
                    clientSecret = paymentIntent.clientSecret,
                    message = "Payment not completed"
                )
            }
        }

        // Main method for PaymentSheet integration
        fun createPaymentSheet(userId: UUID, request: CreatePaymentIntentRequest): PaymentSheetResponse {
            return PaymentService.createPaymentSheet(
                userId = userId,
                addressId = UUID.fromString(request.addressId)
            )
        }

        // Handle webhook events from Stripe
        fun handleWebhookEvent(payload: String, signature: String): Boolean {
            return PaymentService.handleWebhook(payload, signature)
        }
    }
} 