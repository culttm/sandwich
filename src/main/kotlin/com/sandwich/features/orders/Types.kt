package com.sandwich.features.orders

import com.sandwich.common.domain.ExtraItem
import kotlinx.serialization.Serializable

// ── Order lifecycle ──

@Serializable
enum class OrderStatus {
    DRAFT,
    AWAITING_PAYMENT,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}

// ── Delivery ──

@Serializable
data class DeliveryInfo(
    val address: String,
    val phone: String,
    val deliveryTime: String?,
    val deliveryFee: Int
)

// ── Payment ──

@Serializable
enum class PaymentMethod { CARD, CASH_ON_DELIVERY }

@Serializable
data class PaymentInfo(
    val method: PaymentMethod,
    val paidAt: String,
    val transactionId: String?
)

// ── Order types ──

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
    val deliveryFee: Int = 0,
    val total: Int,
    val status: OrderStatus,
    val delivery: DeliveryInfo? = null,
    val payment: PaymentInfo? = null,
    val createdAt: String
)
