package com.codewithfk

import com.codewithfk.configs.FacebookAuthConfig
import com.codewithfk.configs.GoogleAuthConfig
import com.codewithfk.database.DatabaseFactory
import com.codewithfk.database.seedDatabase
import com.codewithfk.routs.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)
    configureRouting()
    install(Authentication) {
        jwt {
            realm = "ktor.io"
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
        oauth("google-oauth") {
            client = HttpClient(CIO) // Apache or CIO
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = GoogleAuthConfig.authorizeUrl,
                    accessTokenUrl = GoogleAuthConfig.tokenUrl,
                    clientId = GoogleAuthConfig.clientId,
                    clientSecret = GoogleAuthConfig.clientSecret,
                    defaultScopes = listOf("profile", "email")
                )
            }
            urlProvider = { GoogleAuthConfig.redirectUri }
        }

        oauth("facebook-oauth") {
            client = HttpClient(CIO)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "facebook",
                    authorizeUrl = FacebookAuthConfig.authorizeUrl,
                    accessTokenUrl = FacebookAuthConfig.tokenUrl,
                    clientId = FacebookAuthConfig.clientId,
                    clientSecret = FacebookAuthConfig.clientSecret,
                    defaultScopes = listOf("public_profile", "email")
                )
            }
            urlProvider = { FacebookAuthConfig.redirectUri }
        }
    }
    DatabaseFactory.init() // Initialize the database\
    seedDatabase()

    routing {
        authRoutes()
        categoryRoutes()
        restaurantRoutes()
        menuItemRoutes()
        authenticate {
            orderRoutes()
            cartRoutes()
            addressRoutes()
        }
    }
}
