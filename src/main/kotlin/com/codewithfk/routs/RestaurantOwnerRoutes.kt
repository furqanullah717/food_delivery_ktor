package com.codewithfk.routs

import com.codewithfk.model.*
import com.codewithfk.services.OrderService
import com.codewithfk.services.RestaurantOwnerService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.restaurantOwnerRoutes() {
    route("/restaurant-owner") {
        authenticate {
            // Get restaurant orders
            get("/orders") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val status = call.request.queryParameters["status"]
                val orders = RestaurantOwnerService.getRestaurantOrders(
                    UUID.fromString(ownerId),
                    status
                )
                call.respond(mapOf("orders" to orders))
            }

            // Update order status
            patch("/orders/{orderId}/status") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@patch call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val orderId = call.parameters["orderId"] ?: return@patch call.respondError(
                    HttpStatusCode.BadRequest,
                    "Order ID is required"
                )
                
                val request = call.receive<UpdateOrderStatusRequest>()
                OrderService.updateOrderStatus(
                    orderId = UUID.fromString(orderId),
                    status = request.status
                )
                call.respond(mapOf("message" to "Order status updated successfully"))
            }

            // Get restaurant statistics
            get("/statistics") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val stats = RestaurantOwnerService.getRestaurantStatistics(UUID.fromString(ownerId))
                call.respond(stats)
            }

            // Get restaurant profile
            get("/profile") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val restaurant = RestaurantOwnerService.getRestaurantDetails(UUID.fromString(ownerId))
                if (restaurant != null) {
                    call.respond(restaurant)
                } else {
                    call.respondError(HttpStatusCode.NotFound, "Restaurant not found")
                }
            }

            // Update restaurant profile
            put("/profile") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@put call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val request = call.receive<UpdateRestaurantRequest>()
                val success = RestaurantOwnerService.updateRestaurantProfile(
                    UUID.fromString(ownerId),
                    request
                )
                
                if (success) {
                    call.respond(mapOf("message" to "Restaurant profile updated successfully"))
                } else {
                    call.respondError(HttpStatusCode.NotFound, "Restaurant not found")
                }
            }

            // Accept/Reject order
            post("/orders/{orderId}/action") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val orderId = call.parameters["orderId"] ?: return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    "Order ID is required"
                )
                
                val request = call.receive<OrderActionRequest>()
                
                try {
                    OrderService.handleOrderAction(
                        orderId = UUID.fromString(orderId),
                        ownerId = UUID.fromString(ownerId),
                        action = request.action,
                        reason = request.reason
                    )
                    call.respond(mapOf("message" to "Order ${request.action.toLowerCase()} successfully"))
                } catch (e: IllegalStateException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Error processing order action")
                }
            }

            // Update order status
            patch("/orders/{orderId}/status") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@patch call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val orderId = call.parameters["orderId"] ?: return@patch call.respondError(
                    HttpStatusCode.BadRequest,
                    "Order ID is required"
                )
                
                val request = call.receive<UpdateOrderStatusRequest>()
                
                try {
                    // Validate status transition
                    val validTransitions = mapOf(
                        OrderStatus.ACCEPTED.name to OrderStatus.PREPARING.name,
                        OrderStatus.PREPARING.name to OrderStatus.READY.name,
                        OrderStatus.READY.name to OrderStatus.OUT_FOR_DELIVERY.name
                    )
                    
                    // Get current order status
                    val currentStatus = OrderService.getOrderDetails(UUID.fromString(orderId)).status
                    
                    if (validTransitions[currentStatus] != request.status) {
                        return@patch call.respondError(
                            HttpStatusCode.BadRequest,
                            "Invalid status transition from $currentStatus to ${request.status}"
                        )
                    }
                    
                    OrderService.updateOrderStatus(
                        orderId = UUID.fromString(orderId),
                        status = request.status
                    )
                    call.respond(mapOf("message" to "Order status updated successfully"))
                } catch (e: IllegalStateException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Error updating order status")
                }
            }
        }
    }
} 