package com.codewithfk.repository

import com.codewithfk.database.OrdersTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*

object PaymentRepository {
    fun updatePaymentStatus(orderId: UUID, status: String, paymentIntentId: String?) {
        transaction {
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[paymentStatus] = status
                it[stripePaymentIntentId] = paymentIntentId
            }
        }
    }
} 