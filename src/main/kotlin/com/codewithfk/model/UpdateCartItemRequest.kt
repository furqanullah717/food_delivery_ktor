package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCartItemRequest(
    val quantity: Int,
    val cartItemId: String
)
