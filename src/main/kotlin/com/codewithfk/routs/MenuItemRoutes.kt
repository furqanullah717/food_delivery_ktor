package com.codewithfk.routs


import com.codewithfk.model.MenuItem
import com.codewithfk.services.MenuItemService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import kotlin.text.get

fun Route.menuItemRoutes() {
    route("/restaurants/{id}/menu") {
        /**
         * Fetch all menu items for a restaurant
         */
        get {
            val restaurantId = call.parameters["id"] ?: return@get call.respondError(
                "Restaurant ID is required.",
                HttpStatusCode.BadRequest
            )
            val menuItems = MenuItemService.getMenuItemsByRestaurant(UUID.fromString(restaurantId))
            call.respond(mapOf("foodItems" to menuItems))
        }

        /**
         * Add a new menu item
         */
        post {
            val restaurantId = call.parameters["id"] ?: return@post call.respondError(
                "Restaurant ID is required.", HttpStatusCode.BadRequest
            )
            val menuItem = call.receive<MenuItem>().copy(restaurantId = restaurantId)
            val itemId = MenuItemService.addMenuItem(menuItem)
            call.respond(mapOf("id" to itemId.toString(), "message" to "Menu item added successfully"))
        }
    }

    route("/menu/{itemId}") {
        /**
         * Update a menu item
         */
        patch {
            val itemId = call.parameters["itemId"] ?: return@patch call.respondError(
                "Menu item ID is required.", HttpStatusCode.BadRequest
            )
            val updatedFields = call.receive<Map<String, Any?>>()
            val success = MenuItemService.updateMenuItem(UUID.fromString(itemId), updatedFields)
            if (success) call.respond(mapOf("message" to "Menu item updated successfully"))
            else call.respondError("Menu item not found", HttpStatusCode.NotFound)
        }

        /**
         * Delete a menu item
         */
        delete {
            val itemId = call.parameters["itemId"] ?: return@delete call.respondError(
                "Menu item ID is required.", HttpStatusCode.BadRequest
            )
            val success = MenuItemService.deleteMenuItem(UUID.fromString(itemId))
            if (success) call.respond(mapOf("message" to "Menu item deleted successfully"))
            else call.respondError("Menu item not found", HttpStatusCode.NotFound)
        }
    }
}