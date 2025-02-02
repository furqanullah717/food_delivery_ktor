package com.codewithfk.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import java.io.FileInputStream

object FirebaseService {
    init {
        try {
            val serviceAccount = FileInputStream("/Users/furqan/Downloads/foodhub-11c1d-firebase-adminsdk-fbsvc-f4c57777a0.json")
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build()

            FirebaseApp.initializeApp(options)
            println("Firebase initialized successfully")
        } catch (e: Exception) {
            println("Firebase initialization failed: ${e.message}")
        }
    }

    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message: $response")
        } catch (e: Exception) {
            println("Error sending Firebase notification: ${e.message}")
        }
    }
} 