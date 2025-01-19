package com.codewithfk.routs

import com.codewithfk.controllers.PaymentController
import com.codewithfk.model.*
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.paymentRoutes() {
    route("/payments") {
        // Webhook endpoint (no authentication required)
        post("/webhook") {
            try {
                val payload = call.receiveText()
                val signature = call.request.header("Stripe-Signature")
                    ?: throw IllegalArgumentException("No signature header")

                val success = PaymentController.handleWebhookEvent(payload, signature)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            } catch (e: Exception) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    e.message ?: "Webhook processing failed"
                )
            }
        }

        // Protected routes
        authenticate {
            // PaymentSheet flow
            post("/create-sheet") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    val response = PaymentController.createPaymentSheet(
                        UUID.fromString(userId),
                        request
                    )
                    call.respond(response)
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        e.message ?: "Error creating payment sheet"
                    )
                }
            }

            post("/create-intent") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    val response = PaymentController.createPaymentSession(
                        UUID.fromString(userId),
                        request
                    )
                    call.respond(response)
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        e.message ?: "Error creating payment intent"
                    )
                }
            }

            post("/confirm/{paymentIntentId}") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val paymentIntentId = call.parameters["paymentIntentId"] 
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "Payment Intent ID required")

                try {
                    val response = PaymentController.confirmAndPlaceOrder(
                        UUID.fromString(userId),
                        paymentIntentId
                    )
                    call.respond(response)
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        e.message ?: "Error confirming payment"
                    )
                }
            }
        }
    }
} 