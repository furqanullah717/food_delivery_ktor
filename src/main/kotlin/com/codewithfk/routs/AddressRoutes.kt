package com.codewithfk.routs

import com.codewithfk.model.Address
import com.codewithfk.model.ReverseGeocodeRequest
import com.codewithfk.services.AddressService
import com.codewithfk.services.GeocodingService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.addressRoutes() {
    route("/addresses") {
        authenticate {
            // Get all addresses for the logged-in user
            get {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val addresses = AddressService.getAddressesByUser(UUID.fromString(userId))
                call.respond(mapOf("addresses" to addresses))
            }

            // Add a new address
            post {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val address = call.receive<Address>().copy(userId = userId)
                val addressId = AddressService.addAddress(address)
                call.respond(HttpStatusCode.Created, mapOf(
                    "id" to addressId.toString(),
                    "message" to "Address added successfully"
                ))
            }

            // Update an existing address
            put("/{id}") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@put call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val addressId = call.parameters["id"] ?: return@put call.respondError(
                    HttpStatusCode.BadRequest,
                    "Address ID is required"
                )
                
                val updatedAddress = call.receive<Address>()
                
                // Verify the address belongs to the user
                val existingAddress = AddressService.getAddressById(UUID.fromString(addressId))
                if (existingAddress?.userId != userId) {
                    return@put call.respondError(HttpStatusCode.Forbidden, "Not authorized to update this address")
                }
                
                val success = AddressService.updateAddress(UUID.fromString(addressId), updatedAddress)
                if (success) {
                    call.respond(mapOf("message" to "Address updated successfully"))
                } else {
                    call.respondError(HttpStatusCode.NotFound, "Address not found")
                }
            }

            // Delete an address
            delete("/{id}") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respondError(HttpStatusCode.Unauthorized, "Unauthorized")
                
                val addressId = call.parameters["id"] ?: return@delete call.respondError(
                    HttpStatusCode.BadRequest,
                    "Address ID is required"
                )
                
                // Verify the address belongs to the user
                val existingAddress = AddressService.getAddressById(UUID.fromString(addressId))
                if (existingAddress?.userId != userId) {
                    return@delete call.respondError(HttpStatusCode.Forbidden, "Not authorized to delete this address")
                }
                
                val success = AddressService.deleteAddress(UUID.fromString(addressId))
                if (success) {
                    call.respond(mapOf("message" to "Address deleted successfully"))
                } else {
                    call.respondError(HttpStatusCode.NotFound, "Address not found")
                }
            }

            post("/reverse-geocode") {
                try {
                    val request = call.receive<ReverseGeocodeRequest>()
                    val address = GeocodingService.reverseGeocode(
                        latitude = request.latitude,
                        longitude = request.longitude
                    )
                    call.respond(address)
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        e.message ?: "Error getting address from coordinates"
                    )
                }
            }
        }
    }
} 