package com.sandwich.common.domain

import kotlinx.serialization.Serializable

// ── Value types ──

@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val price: Int       // ціна в грн
)

@Serializable
data class ExtraItem(
    val id: String,
    val name: String,
    val price: Int       // ціна в грн
)

@Serializable
data class Menu(
    val sandwiches: List<MenuItem>,
    val extras: List<ExtraItem>
)

// ── Order types ──

@Serializable
enum class OrderStatus {
    PENDING, PREPARING, READY, PICKED_UP, CANCELLED
}

@Serializable
data class OrderLine(
    val sandwichId: String,
    val sandwichName: String,
    val sandwichPrice: Int,
    val extras: List<ExtraItem>,
    val lineTotal: Int
)

@Serializable
data class Order(
    val id: String,
    val customerName: String,
    val items: List<OrderLine>,
    val subtotal: Int,
    val discount: Int,
    val total: Int,
    val status: OrderStatus,
    val createdAt: String        // ISO-8601
)
