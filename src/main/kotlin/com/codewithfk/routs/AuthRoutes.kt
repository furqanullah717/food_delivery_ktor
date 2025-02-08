package com.codewithfk.routs

import com.codewithfk.JwtConfig
import com.codewithfk.model.UserRole
import com.codewithfk.services.AuthService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.routing.*

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.runBlocking


data class TokenRequest(val token: String)

fun Route.authRoutes() {


    post("/auth/signup") {
        val params = call.receive<Map<String, String>>()
        val name =
            params["name"] ?: return@post call.respondText("Name is required", status = HttpStatusCode.BadRequest)
        val email =
            params["email"] ?: return@post call.respondText("Email is required", status = HttpStatusCode.BadRequest)
        val passwordHash = params["password"] ?: return@post call.respondText(
            "Password is required",
            status = HttpStatusCode.BadRequest
        )
        val role = params["role"] ?: "customer"

        val token = AuthService.register(name, email, passwordHash, role)
        call.respond(mapOf("token" to token))
    }

    post("/auth/login") {
        val params = call.receive<Map<String, String>>()
        val email =
            params["email"] ?: return@post call.respondText("Email is required", status = HttpStatusCode.BadRequest)
        val passwordHash = params["password"] ?: return@post call.respondText(
            "Password is required",
            status = HttpStatusCode.BadRequest
        )

        val packageName = call.request.header("X-Package-Name")

        val userType = when(packageName){
            "com.codewithfk.foodhub" -> UserRole.CUSTOMER
            "com.codewithfk.foodhub.restaurant" -> UserRole.OWNER
            "com.codewithfk.foodhub.rider" -> UserRole.RIDER
            else -> UserRole.CUSTOMER
        }
        val token = AuthService.login(email, passwordHash,userType)
        if (token != null) {
            call.respond(mapOf("token" to token))
        } else {
            call.respondText("Invalid credentials", status = HttpStatusCode.Unauthorized)
        }
    }
    route("/auth/oauth") {
        post {
            val params = call.receive<Map<String, String>>()
            val provider = params["provider"]
            val token = params["token"]
            val type: String = params["type"] ?: "customer"

            if (provider == null || token == null) {
                call.respondError("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }

            val userInfo = runBlocking {
                when (provider.lowercase()) {
                    "google" -> AuthService.validateGoogleToken(token)
                    "facebook" -> AuthService.validateFacebookToken(token)
                    else -> null
                }
            }

            if (userInfo != null) {
                val email = userInfo["email"] ?: return@post call.respondText(
                    "Email not found",
                    status = HttpStatusCode.BadRequest
                )
                val name = userInfo["name"] ?: "Unknown User"
                val jwt = AuthService.oauthLoginOrRegister(email, name, provider, type)
                call.respond(mapOf("token" to jwt))
            } else {
                call.respondError("Invalid token", status = HttpStatusCode.Unauthorized)
            }
        }
    }
}