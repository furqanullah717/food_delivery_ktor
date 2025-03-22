package com.codewithfk.services

import com.codewithfk.model.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object TrackingService {
    private const val DEVIATION_THRESHOLD_METERS = 100.0 // Recalculate if rider deviates by 100m
    private const val MIN_POINT_DISTANCE_METERS = 20.0 // Minimum distance between points to consider progress
    private const val EARTH_RADIUS_KM = 6371.0 // Earth's radius in kilometers
    private val trackingSessions = ConcurrentHashMap<String, MutableSet<Session>>()
    private val lastCalculatedPaths = ConcurrentHashMap<String, DeliveryPath>() // Cache for paths

    data class Session(
        val sessionId: String,
        val socket: WebSocketSession,
        val role: String
    )

    suspend fun startTracking(orderId: String, session: Session) {
        trackingSessions.getOrPut(orderId) { Collections.synchronizedSet(mutableSetOf()) }.add(session)
        
        val order = OrderService.getOrderDetails(UUID.fromString(orderId))
        val rider = order.riderId?.let { RiderService.getRiderLocation(UUID.fromString(it)) }
        
        if (rider != null) {
            val path = RiderService.getDeliveryPath(UUID.fromString(rider.id), UUID.fromString(orderId))
            session.socket.send(Frame.Text(Json.encodeToString(path)))
        }
    }

    private fun calculateDeviation(
        currentLat: Double, 
        currentLng: Double, 
        polyline: String
    ): Double {
        val decodedPath = com.google.maps.internal.PolylineEncoding.decode(polyline)
        
        return decodedPath.minOf { point: com.google.maps.model.LatLng ->
            calculateDistance(
                currentLat, currentLng,
                point.lat, point.lng
            ) * 1000
        }
    }

    private fun trimPolyline(
        polyline: String,
        currentLat: Double,
        currentLng: Double
    ): String {
        val decodedPath = com.google.maps.internal.PolylineEncoding.decode(polyline)
        
        var closestPointIndex = 0
        var minDistance = Double.MAX_VALUE
        
        decodedPath.forEachIndexed { index, point ->
            val distance = calculateDistance(
                currentLat, currentLng,
                point.lat, point.lng
            ) * 1000
            
            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = index
            }
        }
        
        val remainingPath = decodedPath.subList(closestPointIndex, decodedPath.size)
        
        if (minDistance > MIN_POINT_DISTANCE_METERS) {
            remainingPath.add(
                0,
                com.google.maps.model.LatLng(currentLat, currentLng)
            )
        }
        
        // Re-encode the trimmed path
        return com.google.maps.internal.PolylineEncoding.encode(remainingPath)
    }

    suspend fun updateLocation(locationUpdate: LocationUpdate) {
        val sessions = trackingSessions[locationUpdate.orderId] ?: return
        val cachedPath = lastCalculatedPaths[locationUpdate.orderId]
        
        val path = try {
            if (cachedPath == null) {
                RiderService.getDeliveryPath(
                    UUID.fromString(locationUpdate.riderId),
                    UUID.fromString(locationUpdate.orderId)
                ).also {
                    lastCalculatedPaths[locationUpdate.orderId] = it
                }
            } else {
                val deviation = calculateDeviation(
                    locationUpdate.latitude,
                    locationUpdate.longitude,
                    cachedPath.polyline
                )
                
                if (deviation > DEVIATION_THRESHOLD_METERS) {
                    try {
                        RiderService.getDeliveryPath(
                            UUID.fromString(locationUpdate.riderId),
                            UUID.fromString(locationUpdate.orderId)
                        ).also {
                            lastCalculatedPaths[locationUpdate.orderId] = it
                        }
                    } catch (e: Exception) {
                        updateCachedPath(cachedPath, locationUpdate)
                    }
                } else {
                    updateCachedPath(cachedPath, locationUpdate)
                }
            }
        } catch (e: Exception) {
            if (cachedPath == null) throw e
            updateCachedPath(cachedPath, locationUpdate)
        }

        sessions.forEach { session ->
            try {
                session.socket.send(Frame.Text(Json.encodeToString(path)))
            } catch (e: Exception) {
                sessions.remove(session)
            }
        }
    }

    private fun updateCachedPath(
        cachedPath: DeliveryPath,
        locationUpdate: LocationUpdate
    ): DeliveryPath {
        val trimmedPolyline = trimPolyline(
            cachedPath.polyline,
            locationUpdate.latitude,
            locationUpdate.longitude
        )
        
        return cachedPath.copy(
            currentLocation = Location(
                latitude = locationUpdate.latitude,
                longitude = locationUpdate.longitude,
                address = cachedPath.currentLocation.address
            ),
            polyline = trimmedPolyline
        )
    }

    fun stopTracking(orderId: String, sessionId: String) {
        trackingSessions[orderId]?.removeIf { it.sessionId == sessionId }
        if (trackingSessions[orderId]?.isEmpty() == true) {
            trackingSessions.remove(orderId)
            lastCalculatedPaths.remove(orderId) // Clean up cached path
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(
            sqrt(a),
            sqrt(1 - a)
        )
    }
} 