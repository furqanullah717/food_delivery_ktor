package com.codewithfk.services

import com.codewithfk.model.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TrackingService {
    // Map of orderId to list of tracking sessions
    private val trackingSessions = ConcurrentHashMap<String, MutableSet<Session>>()
    
    data class Session(
        val sessionId: String,
        val socket: WebSocketSession,
        val role: String // RIDER, CUSTOMER, RESTAURANT
    )

    suspend fun startTracking(orderId: String, session: Session) {
        trackingSessions.getOrPut(orderId) { Collections.synchronizedSet(mutableSetOf()) }.add(session)
        
        // Send initial tracking data
        val order = OrderService.getOrderDetails(UUID.fromString(orderId))
        val rider = order.riderId?.let { RiderService.getRiderLocation(UUID.fromString(it)) }
        
        if (rider != null) {
            val path = RiderService.getDeliveryPath(UUID.fromString(rider.id), UUID.fromString(orderId))
            session.socket.send(Frame.Text(Json.encodeToString(path)))
        }
    }

    suspend fun updateLocation(locationUpdate: LocationUpdate) {
        val sessions = trackingSessions[locationUpdate.orderId] ?: return
        
        // Get updated path
        val path = RiderService.getDeliveryPath(
            UUID.fromString(locationUpdate.riderId),
            UUID.fromString(locationUpdate.orderId)
        )
        
        // Broadcast to all connected sessions
        sessions.forEach { session ->
            try {
                session.socket.send(Frame.Text(Json.encodeToString(path)))
            } catch (e: Exception) {
                // Remove failed session
                sessions.remove(session)
            }
        }
    }

    fun stopTracking(orderId: String, sessionId: String) {
        trackingSessions[orderId]?.removeIf { it.sessionId == sessionId }
        if (trackingSessions[orderId]?.isEmpty() == true) {
            trackingSessions.remove(orderId)
        }
    }
} 