package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateMenuItemRequest(
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String? = null,
    val category: String? = null,
    val isAvailable: Boolean = true
)

@Serializable
data class UpdateMenuItemRequest(
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val isAvailable: Boolean? = null
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: String
)

@Serializable
data class RestaurantStatistics(
    val totalOrders: Int,
    val totalRevenue: Double,
    val averageOrderValue: Double,
    val popularItems: List<PopularItem>,
    val ordersByStatus: Map<String, Int>,
    val revenueByDay: List<DailyRevenue>
)

@Serializable
data class PopularItem(
    val id: String,
    val name: String,
    val totalOrders: Int,
    val revenue: Double
)

@Serializable
data class DailyRevenue(
    val date: String,
    val revenue: Double,
    val orders: Int
)

@Serializable
data class UpdateRestaurantRequest(
    val name: String? = null,
    val address: String? = null,
    val categoryId: String? = null,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) 