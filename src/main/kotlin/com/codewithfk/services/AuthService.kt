package com.codewithfk.services


import com.codewithfk.JwtConfig
import com.codewithfk.database.UsersTable
import com.codewithfk.model.AuthProvider
import com.codewithfk.model.UserRole
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object AuthService {
    private val httpClient = HttpClient(CIO)

    fun register(name: String, email: String, passwordHash: String, role: String): String {
        return transaction {
            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[this.name] = name
                it[this.email] = email
                it[this.passwordHash] = passwordHash
                it[this.role] = role
                it[this.authProvider] = "email"
            }
            val address = AddressService.getAddressesByUser(userId)
            if (address.isEmpty()) {
                AddressService.createDefaultAddress(userId)
            }
            JwtConfig.generateToken(userId.toString())
        }
    }

    fun getUserEmailFromID(userId: UUID): String? {
        return transaction {
            val user = UsersTable.select { UsersTable.id eq userId }.singleOrNull()
            user?.get(UsersTable.email)
        }
    }

    fun login(email: String, passwordHash: String): String? {
        return transaction {
            val user = UsersTable.select {
                (UsersTable.email eq email) and (UsersTable.passwordHash eq passwordHash)
            }.singleOrNull()

            user?.let {
                val userId = it[UsersTable.id]
                val address = AddressService.getAddressesByUser(userId)
                if (address.isEmpty()) {
                    AddressService.createDefaultAddress(userId)
                }
                JwtConfig.generateToken(userId.toString())
            }
        }
    }

    // Google OAuth User Info
    /**
     * Validate Google ID Token and get user information.
     */
    suspend fun validateGoogleToken(idToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject

        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    /**
     * Validate Facebook Access Token and get user information.
     */
    suspend fun validateFacebookToken(accessToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://graph.facebook.com/me") {
            parameter("fields", "id,name,email")
            parameter("access_token", accessToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject


        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    /**
     * Handle user registration or login based on OAuth provider.
     */
    fun oauthLoginOrRegister(email: String, name: String, provider: String, userType: String): String {
        return transaction {
            val user = UsersTable.select { UsersTable.email eq email }.singleOrNull()

            if (user == null) {
                // Register a new user
                val userId = UUID.randomUUID()
                UsersTable.insert {
                    it[id] = userId
                    it[this.email] = email
                    it[this.name] = name
                    it[this.authProvider] = provider
                    it[this.role] = userType
                }
                JwtConfig.generateToken(userId.toString())
            } else {
                // Generate token for existing user
                val userId = user[UsersTable.id]
                JwtConfig.generateToken(userId.toString())
            }
        }
    }

}