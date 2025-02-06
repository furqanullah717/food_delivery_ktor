package com.codewithfk.routs

import com.codewithfk.model.*
import com.codewithfk.services.RiderService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.riderRoutes() {
    route("/rider") {
        authenticate {
            // Update rider location
            post("/location") {
                val riderId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                val location = call.receive<Location>()
                RiderService.updateRiderLocation(
                    UUID.fromString(riderId),
                    location.latitude,
                    location.longitude
                )
                call.respond(mapOf("message" to "Location updated successfully"))
            }

            // Accept delivery request
            post("/deliveries/{orderId}/accept") {
                val riderId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                val orderId = call.parameters["orderId"] ?: return@post call.respondError(
                    HttpStatusCode.BadRequest,
                    "Order ID is required"
                )

                val accepted = RiderService.acceptDeliveryRequest(
                    UUID.fromString(riderId),
                    UUID.fromString(orderId)
                )

                if (accepted) {
                    call.respond(mapOf("message" to "Delivery request accepted"))
                } else {
                    call.respondError(HttpStatusCode.BadRequest, "Failed to accept delivery request")
                }
            }

            // Get delivery path
            get("/deliveries/{orderId}/path") {
                val riderId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")

                val orderId = call.parameters["orderId"] ?: return@get call.respondError(
                    HttpStatusCode.BadRequest,
                    "Order ID is required"
                )

                val path = RiderService.getDeliveryPath(
                    UUID.fromString(riderId),
                    UUID.fromString(orderId)
                )
                call.respond(path)
            }
        }
    }
} 