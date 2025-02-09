package com.codewithfk.database.migrations

import com.codewithfk.database.UsersTable
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun updateOwnerPassword() {
    transaction {
        // Hash the new password
        val newPassword = "111111"

        // Update the password for owner1@example.com
        val updatedRows = UsersTable.update({ (UsersTable.email eq "owner1@example.com") or (UsersTable.email eq "owner2@example.com") }) {
            it[passwordHash] = newPassword
        }
        if (updatedRows > 0) {
            println("Password updated successfully for owner1@example.com")
        } else {
            println("No user found with email owner1@example.com")
        }
    }
} 