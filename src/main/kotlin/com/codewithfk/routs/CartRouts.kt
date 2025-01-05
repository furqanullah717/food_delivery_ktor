package com.codewithfk.routs

import com.codewithfk.model.AddToCartRequest
import com.codewithfk.model.CartItem
import com.codewithfk.model.CheckoutModel
import com.codewithfk.model.UpdateCartItemRequest
import com.codewithfk.services.CartService
import com.codewithfk.services.OrderService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CartResponse(
    val items: List<CartItem>,
    val checkoutDetails: CheckoutModel
)

fun Route.cartRoutes() {
    route("/cart") {
        /**
         * Fetch all items in the cart
         */
        get {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respondError("Unauthorized.", HttpStatusCode.Unauthorized)
            val cartItems = CartService.getCartItems(UUID.fromString(userId))
            val checkoutDetails = OrderService.getCheckoutDetails(UUID.fromString(userId))
            call.respond(
                CartResponse(
                    items = cartItems,
                    checkoutDetails = checkoutDetails
                )
            )
        }

        /**
         * Add an item to the cart
         */
        post {
            val uid = call.principal<JWTPrincipal>()?.payload
            val userId = uid?.getClaim("userId")?.asString()
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized.")
            val request = call.receive<AddToCartRequest>()
            val restaurantId = UUID.fromString(
                request.restaurantId as? String ?: return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    "Restaurant ID is required."
                )
            )
            val menuItemId = UUID.fromString(
                request.menuItemId as? String ?: return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    "Menu item ID is required."
                )
            )
            val quantity = (request.quantity as? Int) ?: return@post call.respondError(
                HttpStatusCode.BadRequest,
                "Quantity is required."
            )

            val cartItemId = CartService.addToCart(UUID.fromString(userId), restaurantId, menuItemId, quantity)
            call.respond(mapOf("id" to cartItemId.toString(), "message" to "Item added to cart"))
        }

        /**
         * Update item quantity in the cart
         */
        patch {
            val cartItem = call.receive<UpdateCartItemRequest>()
            val quantity = cartItem.quantity
            if (quantity == 0) {
                call.respondError(HttpStatusCode.BadRequest, "Quantity cannot be zero")
            }

            val success = CartService.updateCartItemQuantity(UUID.fromString(cartItem.cartItemId), quantity)
            if (success) call.respond(mapOf("message" to "Cart item updated successfully"))
            else call.respondError(HttpStatusCode.NotFound, "Cart item not found")
        }

        /**
         * Remove an item from the cart
         */
        delete("/{cartItemId}") {
            val cartItemId = call.parameters["cartItemId"] ?: return@delete call.respondError(
                HttpStatusCode.BadRequest,
                "Cart item ID is required."
            )

            val success = CartService.removeCartItem(UUID.fromString(cartItemId))
            if (success) call.respond(mapOf("message" to "Cart item removed successfully"))
            else call.respondError(HttpStatusCode.NotFound, "Cart item not found")
        }

        /**
         * Clear the cart
         */
        delete {
            val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                ?: return@delete call.respondError(HttpStatusCode.Unauthorized, "Unauthorized.")
            val success = CartService.clearCart(UUID.fromString(userId))
            if (success) call.respond(mapOf("message" to "Cart cleared successfully"))
            else call.respondError(HttpStatusCode.BadRequest, "Failed to clear the cart")
        }
    }
}