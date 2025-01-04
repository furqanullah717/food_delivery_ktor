package com.codewithfk.services

import com.codewithfk.database.CartTable
import com.codewithfk.database.MenuItemsTable
import com.codewithfk.model.CartItem
import com.codewithfk.model.MenuItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object CartService {

    fun getCartItems(userId: UUID): List<CartItem> {
        return transaction {
            (CartTable innerJoin MenuItemsTable)
                .select { CartTable.userId eq userId }
                .map {
                    CartItem(
                        id = it[CartTable.id].toString(),
                        userId = it[CartTable.userId].toString(),
                        restaurantId = it[CartTable.restaurantId].toString(),
                        menuItemId = MenuItem(
                            id = it[MenuItemsTable.id].toString(),
                            name = it[MenuItemsTable.name],
                            description = it[MenuItemsTable.description],
                            price = it[MenuItemsTable.price],
                            restaurantId = it[MenuItemsTable.restaurantId].toString(),
                            imageUrl = it[MenuItemsTable.imageUrl]
                        ),
                        quantity = it[CartTable.quantity],
                        addedAt = it[CartTable.addedAt].toString()
                    )
                }
        }
    }

    fun addToCart(userId: UUID, restaurantId: UUID, menuItemId: UUID, quantity: Int): UUID {
        return transaction {
            // Check if item is already in the cart
            val existingItem = CartTable.select {
                (CartTable.userId eq userId) and (CartTable.menuItemId eq menuItemId)
            }.singleOrNull()

            if (existingItem != null) {
                // Update the quantity if the item exists
                CartTable.update({ CartTable.id eq existingItem[CartTable.id] }) {
                    it[CartTable.quantity] = existingItem[CartTable.quantity] + quantity
                }
                UUID.fromString(existingItem[CartTable.id].toString())
            } else {
                // Add a new item
                CartTable.insert {
                    it[this.userId] = userId
                    it[this.restaurantId] = restaurantId
                    it[this.menuItemId] = menuItemId
                    it[this.quantity] = quantity
                } get CartTable.id
            }
        }
    }

    fun updateCartItemQuantity(cartItemId: UUID, quantity: Int): Boolean {
        return transaction {
            CartTable.update({ CartTable.id eq cartItemId }) {
                it[this.quantity] = quantity
            } > 0
        }
    }

    fun removeCartItem(cartItemId: UUID): Boolean {
        return transaction {
            CartTable.deleteWhere { CartTable.id eq cartItemId } > 0
        }
    }

    fun clearCart(userId: UUID): Boolean {
        return transaction {
            CartTable.deleteWhere { CartTable.userId eq userId } > 0
        }
    }
}