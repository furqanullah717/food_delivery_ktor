package com.codewithfk.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object RiderLocationsTable : Table("rider_locations") {
    val id = uuid("id").autoGenerate()
    val riderId = uuid("rider_id").references(UsersTable.id)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val isAvailable = bool("is_available")
    val lastUpdated = datetime("last_updated")

    override val primaryKey = PrimaryKey(id)
}

object DeliveryRequestsTable : Table("delivery_requests") {
    val id = uuid("id").autoGenerate()
    val orderId = uuid("order_id").references(OrdersTable.id)
    val riderId = uuid("rider_id").references(UsersTable.id)
    val status = varchar("status", 50) // PENDING, ACCEPTED, REJECTED, CANCELLED
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
} 