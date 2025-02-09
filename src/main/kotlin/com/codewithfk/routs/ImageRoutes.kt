package com.codewithfk.routs

import com.codewithfk.services.ImageService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.imageRoutes() {
    route("/images") {
        authenticate {
            post("/upload") {
                try {
                    // Get user role from JWT
                    val principal = call.principal<JWTPrincipal>()
                    val userRole = "owner"

                    // Determine folder based on user role
                    val folder = when (userRole.lowercase()) {
                        "owner" -> "restaurants"
                        "customer" -> "customers"
                        "rider" -> "riders"
                        else -> "misc"
                    }

                    // Handle multipart data
                    val multipart = call.receiveMultipart()
                    val imageUrl = ImageService.uploadImage(multipart, folder)

                    call.respond(hashMapOf("url" to imageUrl))
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        e.message ?: "Error uploading image"
                    )
                }
            }

            delete("/{imageUrl}") {
                try {
                    val imageUrl = call.parameters["imageUrl"] 
                        ?: return@delete call.respondError(HttpStatusCode.BadRequest, "Image URL required")
                    
                    ImageService.deleteImage(imageUrl)
                    call.respond(hashMapOf("message" to "Image deleted successfully"))
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        e.message ?: "Error deleting image"
                    )
                }
            }
        }
    }
} 