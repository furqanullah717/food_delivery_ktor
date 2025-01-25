package com.codewithfk.services

import com.codewithfk.database.NotificationsTable
import com.codewithfk.database.UsersTable
import com.codewithfk.model.Notification
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object NotificationService {
    fun createNotification(
        userId: UUID,
        title: String,
        message: String,
        type: String,
        orderId: UUID? = null
    ): UUID {
        return transaction {
            // Save notification to database
            val notificationId = NotificationsTable.insert {
                it[NotificationsTable.userId] = userId
                it[NotificationsTable.title] = title
                it[NotificationsTable.message] = message
                it[NotificationsTable.type] = type
                it[NotificationsTable.orderId] = orderId
                it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
            } get NotificationsTable.id

            // Get user's FCM token
            val fcmToken = UsersTable
                .select { UsersTable.id eq userId }
                .map { it[UsersTable.fcmToken] }
                .singleOrNull()

            // Send push notification if token exists
            fcmToken?.let {
                FirebaseService.sendNotification(
                    token = it,
                    title = title,
                    body = message,
                    data = mapOf(
                        "type" to type,
                        "orderId" to (orderId?.toString() ?: ""),
                        "notificationId" to notificationId.toString()
                    )
                )
            }

            notificationId
        }
    }

    fun getNotifications(userId: UUID): List<Notification> {
        return transaction {
            NotificationsTable
                .select { NotificationsTable.userId eq userId }
                .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
                .map {
                    Notification(
                        id = it[NotificationsTable.id].toString(),
                        userId = it[NotificationsTable.userId].toString(),
                        title = it[NotificationsTable.title],
                        message = it[NotificationsTable.message],
                        type = it[NotificationsTable.type],
                        orderId = it[NotificationsTable.orderId]?.toString(),
                        isRead = it[NotificationsTable.isRead],
                        createdAt = it[NotificationsTable.createdAt].toString()
                    )
                }
        }
    }

    fun markAsRead(notificationId: UUID) {
        transaction {
            NotificationsTable.update({ NotificationsTable.id eq notificationId }) {
                it[isRead] = true
            }
        }
    }

    fun getUnreadCount(userId: UUID): Int {
        return transaction {
            NotificationsTable
                .select { (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) }
                .count()
                .toInt()
        }
    }
} 