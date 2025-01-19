package com.codewithfk.routs

import com.codewithfk.model.PlaceOrderRequest
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
import kotlin.text.get

fun Route.orderRoutes() {
    route("/orders") {

        /**
         * Place an order
         */
        post {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

            try {
                val request = call.receive<PlaceOrderRequest>()
                val orderId = OrderService.placeOrder(UUID.fromString(userId), request)
                call.respond(mapOf("id" to orderId.toString(), "message" to "Order placed successfully"))
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.BadRequest, e.message ?: "Error placing order")
            }
        }

        /**
         * Fetch all orders for the logged-in user
         */
        get {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized.")
            val orders = OrderService.getOrdersByUser(UUID.fromString(userId))
            call.respond(mapOf("orders" to orders))
        }

        /**
         * Fetch details of a specific order
         */
        get("/{id}") {
            val orderId = call.parameters["id"] ?: return@get call.respondError(
                HttpStatusCode.BadRequest,
                "Order ID is required."
            )
            try {
                val order = OrderService.getOrderDetails(UUID.fromString(orderId))
                call.respond(order)
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.NotFound, e.message ?: "Order not found")
            }
        }

        /**
         * Update order status
         */
        patch("/{id}/status") {
            val orderId = call.parameters["id"] ?: return@patch call.respondError(
                HttpStatusCode.BadRequest,
                "Order ID is required."
            )
            val params = call.receive<Map<String, String>>()
            val status =
                params["status"] ?: return@patch call.respondError(HttpStatusCode.BadRequest, "Status is required.")

            val success = OrderService.updateOrderStatus(UUID.fromString(orderId), status)
            if (success) call.respond(mapOf("message" to "Order status updated successfully"))
            else call.respondError(HttpStatusCode.NotFound, "Order not found")
        }
    }
}