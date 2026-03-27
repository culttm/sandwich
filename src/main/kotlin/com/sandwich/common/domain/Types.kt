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

// ── Order lifecycle ──

@Serializable
enum class OrderStatus {
    DRAFT,               // чернетка — обрано items
    AWAITING_PAYMENT,    // доставка вказана, чекаємо оплату
    PREPARING,           // оплачено → кухня готує
    OUT_FOR_DELIVERY,    // кур'єр везе
    DELIVERED,           // доставлено
    CANCELLED            // скасовано
}

// ── Delivery ──

@Serializable
data class DeliveryInfo(
    val address: String,
    val phone: String,
    val deliveryTime: String?,   // бажаний час, null = "якнайшвидше"
    val deliveryFee: Int         // вартість доставки в грн
)

// ── Payment ──

@Serializable
enum class PaymentMethod { CARD, CASH_ON_DELIVERY }

@Serializable
data class PaymentInfo(
    val method: PaymentMethod,
    val paidAt: String,          // ISO-8601
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
    val createdAt: String        // ISO-8601
)
