package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val id: String,
    val userId: String,
    val restaurantId: String,
    val menuItemId: MenuItem?,
    val quantity: Int,
    val addedAt: String
)