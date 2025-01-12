package com.codewithfk.routs

import com.codewithfk.model.CreatePaymentIntentRequest
import com.codewithfk.model.ConfirmPaymentRequest
import com.codewithfk.model.PlaceOrderRequest
import com.codewithfk.services.PaymentService
import com.codewithfk.services.OrderService
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
        authenticate {
            // Create payment intent
            post("/create-intent") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    val request = call.receive<CreatePaymentIntentRequest>()
                    val paymentIntent = PaymentService.createPaymentIntent(
                        UUID.fromString(userId),
                        UUID.fromString(request.addressId)
                    )
                    call.respond(paymentIntent)
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Error creating payment intent")
                }
            }

            // Confirm payment and place order
            post("/confirm") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                try {
                    val request = call.receive<ConfirmPaymentRequest>()
                    
                    // Verify payment was successful
                    val paymentIntent = PaymentService.confirmPayment(
                        UUID.fromString(userId),
                        request.paymentIntentId
                    )

                    // Place the order
                    val orderId = OrderService.placeOrder(
                        userId = UUID.fromString(userId),
                        request = PlaceOrderRequest(
                            addressId = request.addressId
                        ),
                        paymentIntentId = paymentIntent.id
                    )

                    call.respond(mapOf(
                        "orderId" to orderId.toString(),
                        "message" to "Payment confirmed and order placed successfully"
                    ))
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Error confirming payment")
                }
            }
        }
    }
} 