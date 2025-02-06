package com.codewithfk.services

import com.codewithfk.database.*
import com.codewithfk.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.ExpressionAlias

object RestaurantOwnerService {
    fun getRestaurantOrders(ownerId: UUID, status: String? = null): List<Order> {
        return transaction {
            val query = (OrdersTable
                .join(RestaurantsTable, JoinType.INNER, OrdersTable.restaurantId, RestaurantsTable.id)
                .join(UsersTable, JoinType.INNER, OrdersTable.userId, UsersTable.id)
                .join(AddressesTable, JoinType.LEFT, OrdersTable.addressId, AddressesTable.id)
                .select { RestaurantsTable.ownerId eq ownerId })
                
            status?.let {
                query.andWhere { OrdersTable.status eq status }
            }

            query.orderBy(OrdersTable.createdAt, SortOrder.DESC)
                .map { row ->
                    val orderId = row[OrdersTable.id]
                    
                    // Get order items
                    val items = OrderItemsTable
                        .join(MenuItemsTable, JoinType.INNER, OrderItemsTable.menuItemId, MenuItemsTable.id)
                        .select { OrderItemsTable.orderId eq orderId }
                        .map { itemRow ->
                            OrderItem(
                                id = itemRow[OrderItemsTable.id].toString(),
                                orderId = orderId.toString(),
                                menuItemId = itemRow[OrderItemsTable.menuItemId].toString(),
                                quantity = itemRow[OrderItemsTable.quantity]
                            )
                        }

                    // Map address
                    val address = if (row.getOrNull(AddressesTable.id) != null) {
                        Address(
                            id = row[AddressesTable.id].toString(),
                            userId = row[AddressesTable.userId].toString(),
                            addressLine1 = row[AddressesTable.addressLine1],
                            addressLine2 = row[AddressesTable.addressLine2],
                            city = row[AddressesTable.city],
                            state = row[AddressesTable.state],
                            zipCode = row[AddressesTable.zipCode],
                            country = row[AddressesTable.country],
                            latitude = row[AddressesTable.latitude],
                            longitude = row[AddressesTable.longitude]
                        )
                    } else null

                    Order(
                        id = orderId.toString(),
                        userId = row[OrdersTable.userId].toString(),
                        restaurantId = row[OrdersTable.restaurantId].toString(),
                        address = address,
                        status = row[OrdersTable.status],
                        paymentStatus = row[OrdersTable.paymentStatus],
                        stripePaymentIntentId = row[OrdersTable.stripePaymentIntentId],
                        totalAmount = row[OrdersTable.totalAmount],
                        items = items,
                        restaurant = Restaurant(
                            id = row[RestaurantsTable.id].toString(),
                            ownerId = row[RestaurantsTable.ownerId].toString(),
                            name = row[RestaurantsTable.name],
                            address = row[RestaurantsTable.address],
                            categoryId = row[RestaurantsTable.categoryId].toString(),
                            latitude = row[RestaurantsTable.latitude],
                            longitude = row[RestaurantsTable.longitude],
                            imageUrl = row[RestaurantsTable.imageUrl] ?: "",
                            createdAt = row[RestaurantsTable.createdAt].toString()
                        ),
                        createdAt = row[OrdersTable.createdAt].toString(),
                        updatedAt = row[OrdersTable.updatedAt].toString(),
                        riderId = row[OrdersTable.riderId]?.toString()
                    )
                }
        }
    }

    fun getRestaurantStatistics(ownerId: UUID): RestaurantStatistics {
        return transaction {
            // Get restaurant ID
            val restaurantId = RestaurantsTable
                .select { RestaurantsTable.ownerId eq ownerId }
                .map { it[RestaurantsTable.id] }
                .firstOrNull() ?: throw IllegalStateException("Restaurant not found")

            // Get all completed orders
            val orders = OrdersTable
                .select { 
                    (OrdersTable.restaurantId eq restaurantId) and
                    (OrdersTable.status inList listOf("Delivered", "Completed"))
                }
                .toList()

            val totalOrders = orders.size
            val totalRevenue = orders.sumOf { it[OrdersTable.totalAmount] }
            val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

            // Calculate orders by status
            val ordersByStatus = OrdersTable
                .slice(OrdersTable.status, OrdersTable.id.count())
                .select { OrdersTable.restaurantId eq restaurantId }
                .groupBy(OrdersTable.status)
                .associate { 
                    it[OrdersTable.status] to it[OrdersTable.id.count()].toInt()
                }

            // Calculate popular items with proper join and grouping
            val revenueColumn = (OrderItemsTable.quantity.sum().castTo<Double>(DoubleColumnType()) * MenuItemsTable.price)
                .alias("total_revenue")

            val popularItems = (OrderItemsTable
                .join(MenuItemsTable, JoinType.INNER)
                .join(OrdersTable, JoinType.INNER, OrderItemsTable.orderId, OrdersTable.id)
                .slice(
                    MenuItemsTable.id,
                    MenuItemsTable.name,
                    OrderItemsTable.quantity.sum(),
                    revenueColumn
                )
                .select { OrdersTable.restaurantId eq restaurantId }
                .groupBy(MenuItemsTable.id, MenuItemsTable.name, MenuItemsTable.price)
                .orderBy(OrderItemsTable.quantity.sum(), SortOrder.DESC)
                .limit(10)
                .map {
                    PopularItem(
                        id = it[MenuItemsTable.id].toString(),
                        name = it[MenuItemsTable.name],
                        totalOrders = it[OrderItemsTable.quantity.sum()]?.toInt() ?: 0,
                        revenue = it[revenueColumn].toDouble() ?: 0.0
                    )
                })

            // Calculate daily revenue with proper date grouping
            val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
            val revenueByDay = OrdersTable
                .slice(
                    OrdersTable.createdAt,
                    OrdersTable.totalAmount.sum(),
                    OrdersTable.id.count()
                )
                .select { 
                    (OrdersTable.restaurantId eq restaurantId) and
                    (OrdersTable.createdAt greaterEq thirtyDaysAgo)
                }
                .groupBy(OrdersTable.createdAt)
                .map {
                    DailyRevenue(
                        date = it[OrdersTable.createdAt].toString(),
                        revenue = it[OrdersTable.totalAmount.sum()]?.toDouble() ?: 0.0,
                        orders = it[OrdersTable.id.count()].toInt()
                    )
                }

            RestaurantStatistics(
                totalOrders = totalOrders,
                totalRevenue = totalRevenue,
                averageOrderValue = averageOrderValue,
                popularItems = popularItems,
                ordersByStatus = ordersByStatus,
                revenueByDay = revenueByDay
            )
        }
    }

    fun getRestaurantDetails(ownerId: UUID): Restaurant? {
        return transaction {
            RestaurantsTable
                .select { RestaurantsTable.ownerId eq ownerId }
                .map { row ->
                    Restaurant(
                        id = row[RestaurantsTable.id].toString(),
                        ownerId = row[RestaurantsTable.ownerId].toString(),
                        name = row[RestaurantsTable.name],
                        address = row[RestaurantsTable.address],
                        categoryId = row[RestaurantsTable.categoryId].toString(),
                        latitude = row[RestaurantsTable.latitude],
                        longitude = row[RestaurantsTable.longitude],
                        imageUrl = row[RestaurantsTable.imageUrl] ?: "",
                        createdAt = row[RestaurantsTable.createdAt].toString()
                    )
                }
                .firstOrNull()
        }
    }

    fun updateRestaurantProfile(ownerId: UUID, request: UpdateRestaurantRequest): Boolean {
        return transaction {
            RestaurantsTable.update({ RestaurantsTable.ownerId eq ownerId }) {
                request.name?.let { name -> it[RestaurantsTable.name] = name }
                request.address?.let { addr -> it[address] = addr }
                request.imageUrl?.let { url -> it[imageUrl] = url }
                request.categoryId?.let { catId -> it[categoryId] = UUID.fromString(catId) }
                request.latitude?.let { lat -> it[latitude] = lat }
                request.longitude?.let { lon -> it[longitude] = lon }
            } > 0
        }
    }
} 