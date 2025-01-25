package com.codewithfk.database

import com.codewithfk.model.Category
import io.ktor.http.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseFactory {
    fun init() {
        val driverClassName = "com.mysql.cj.jdbc.Driver"
        val jdbcURL = "jdbc:mysql://localhost:3306/food_delivery"
        val user = "root"
        val password = "root"  // Change this to your MySQL password

        try {
            Class.forName(driverClassName)
            Database.connect(jdbcURL, driverClassName, user, password)

            transaction {
                // Create tables if they don't exist
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable,
                    CategoriesTable,
                    RestaurantsTable,
                    MenuItemsTable,
                    AddressesTable,
                    OrdersTable,
                    OrderItemsTable,
                    CartTable
                )
            }
        } catch (e: Exception) {
            println("Database initialization failed: ${e.message}")
            throw e
        }
    }
}

fun Application.migrateDatabase() {
    transaction {
        try {
            // First check if column exists
            val columnExists = exec("""
                SELECT COUNT(*) 
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'users' 
                AND COLUMN_NAME = 'fcm_token'
            """) { it.next(); it.getInt(1) } ?: 0 > 0

            // Add column if it doesn't exist
            if (!columnExists) {
                exec("""
                    ALTER TABLE users 
                    ADD COLUMN fcm_token VARCHAR(255) NULL
                """)
                println("Added fcm_token column to users table")
            } else {
                println("fcm_token column already exists")
            }
            
            println("Migration completed successfully")
        } catch (e: Exception) {
            println("Migration failed: ${e.message}")
            throw e
        }
    }
}

fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            // Seed users if none exist
            if (UsersTable.selectAll().empty()) {
                println("Seeding users...")
                val users = listOf(
                    Triple(UUID.randomUUID(), "owner1@example.com", "Restaurant Owner"),
                    Triple(UUID.randomUUID(), "owner2@example.com", "Another Owner")
                )

                users.forEach { (id, email, name) ->
                    UsersTable.insert {
                        it[UsersTable.id] = id
                        it[UsersTable.email] = email
                        it[UsersTable.name] = name
                        it[UsersTable.role] = "OWNER"
                        it[UsersTable.authProvider] = "email"
                        it[UsersTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                }
            }

            // Seed categories if none exist
            if (CategoriesTable.selectAll().empty()) {
                println("Seeding categories...")
                val categories = listOf(
                    Pair("Pizza", "pizza.jpg"),
                    Pair("Burger", "burger.jpg"),
                    Pair("Sushi", "sushi.jpg"),
                    Pair("Indian", "indian.jpg"),
                    Pair("Mexican", "mexican.jpg"),
                    Pair("Italian", "italian.jpg"),
                    Pair("Chinese", "chinese.jpg"),
                    Pair("Thai", "thai.jpg")
                )

                categories.forEach { (name, image) ->
                    CategoriesTable.insert {
                        it[id] = UUID.randomUUID()
                        it[CategoriesTable.name] = name
                        it[imageUrl] = image
                        it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                }
            }

            // Seed restaurants if none exist
            if (RestaurantsTable.selectAll().empty()) {
                println("Seeding restaurants...")
                val categoryId = CategoriesTable.select { CategoriesTable.name eq "Pizza" }
                    .map { it[CategoriesTable.id] }
                    .firstOrNull()

                categoryId?.let {
                    RestaurantsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[ownerId] = UsersTable.select { UsersTable.email eq "owner1@example.com" }
                            .map { it[UsersTable.id] }
                            .first()
                        it[name] = "Pizza Palace"
                        it[address] = "123 Main St"
                        it[RestaurantsTable.categoryId] = categoryId
                        it[latitude] = 40.7128
                        it[longitude] = -74.0060
                        it[imageUrl] = "pizza_palace.jpg"
                        it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                }
            }
        }
    }
}