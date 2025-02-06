package com.codewithfk.routs

import com.codewithfk.model.*
import com.codewithfk.services.TrackingService
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.*

fun Route.trackingRoutes() {
    webSocket("/track/{orderId}") {
        try {
            val orderId = call.parameters["orderId"] ?: throw IllegalArgumentException("Order ID required")
            val sessionId = UUID.randomUUID().toString()
            
            // Start tracking session
            val session = TrackingService.Session(
                sessionId = sessionId,
                socket = this,
                role = call.request.queryParameters["role"] ?: "CUSTOMER"
            )
            TrackingService.startTracking(orderId, session)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val locationUpdate = Json.decodeFromString<LocationUpdate>(frame.readText())
                        TrackingService.updateLocation(locationUpdate)
                    }
                }
            } finally {
                TrackingService.stopTracking(orderId, sessionId)
            }
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.message ?: "Error"))
        }
    }
} 