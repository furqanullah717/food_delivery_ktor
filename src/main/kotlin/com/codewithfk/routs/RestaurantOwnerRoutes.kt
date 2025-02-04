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
                call.respond(orders)
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

            // Add menu item
            post("/menu-items") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val request = call.receive<CreateMenuItemRequest>()
                val menuItemId = RestaurantOwnerService.addMenuItem(
                    ownerId = UUID.fromString(ownerId),
                    request = request
                )
                call.respond(mapOf("id" to menuItemId.toString(), "message" to "Menu item added successfully"))
            }

            // Update menu item
            put("/menu-items/{itemId}") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@put call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val itemId = call.parameters["itemId"] ?: return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    "Item ID is required"
                )
                
                val request = call.receive<UpdateMenuItemRequest>()
                RestaurantOwnerService.updateMenuItem(
                    ownerId = UUID.fromString(ownerId),
                    itemId = UUID.fromString(itemId),
                    request = request
                )
                call.respond(mapOf("message" to "Menu item updated successfully"))
            }

            // Delete menu item
            delete("/menu-items/{itemId}") {
                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val itemId = call.parameters["itemId"] ?: return@delete call.respondError(
                    HttpStatusCode.BadRequest,
                    "Item ID is required"
                )
                
                RestaurantOwnerService.deleteMenuItem(
                    ownerId = UUID.fromString(ownerId),
                    itemId = UUID.fromString(itemId)
                )
                call.respond(mapOf("message" to "Menu item deleted successfully"))
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
        }
    }
} 