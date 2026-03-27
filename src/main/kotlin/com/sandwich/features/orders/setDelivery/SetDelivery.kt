package com.sandwich.features.orders.setDelivery

import com.sandwich.common.domain.*
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Крок 2: Вказати доставку → AWAITING_PAYMENT
// ══════════════════════════════════════════════════════════════

// ── Request DTO ──

@Serializable
data class SetDeliveryRequest(
    val address: String,
    val phone: String,
    val deliveryTime: String? = null   // null = "якнайшвидше"
)

// ── Decision ──

sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data object NotFound : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
    data class BlankAddress(val message: String = "Вкажіть адресу") : SetDeliveryDecision
    data class BlankPhone(val message: String = "Вкажіть телефон") : SetDeliveryDecision
}

// ── Pure logic (NOT suspend) ──

fun decideDelivery(
    order: Order?,
    address: String,
    phone: String,
    deliveryTime: String?
): SetDeliveryDecision {
    if (order == null)
        return SetDeliveryDecision.NotFound

    if (order.status != OrderStatus.DRAFT)
        return SetDeliveryDecision.WrongStatus(order.status)

    if (address.isBlank())
        return SetDeliveryDecision.BlankAddress()

    if (phone.isBlank())
        return SetDeliveryDecision.BlankPhone()

    val deliveryFee = calculateDeliveryFee(order.subtotal)
    val delivery = DeliveryInfo(
        address = address.trim(),
        phone = phone.trim(),
        deliveryTime = deliveryTime,
        deliveryFee = deliveryFee
    )

    return SetDeliveryDecision.DeliverySet(
        order.copy(
            status = OrderStatus.AWAITING_PAYMENT,
            delivery = delivery,
            deliveryFee = deliveryFee,
            total = order.subtotal - order.discount + deliveryFee
        )
    )
}

// ── Response DTOs ──

@Serializable
data class SetDeliveryResponse(
    val orderId: String,
    val deliveryFee: Int,
    val total: Int
)

@Serializable
data class SetDeliveryError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun SetDelivery(db: Db): suspend (String, SetDeliveryRequest) -> Any = handler@{ orderId, request ->

    // 🔴 READ
    val order = db.orders[orderId]

    // 🟢 CALCULATE
    val decision = decideDelivery(order, request.address, request.phone, request.deliveryTime)

    // 🔴 WRITE
    when (decision) {
        is SetDeliveryDecision.DeliverySet -> {
            db.orders[decision.order.id] = decision.order
            SetDeliveryResponse(
                orderId = decision.order.id,
                deliveryFee = decision.order.deliveryFee,
                total = decision.order.total
            )
        }
        is SetDeliveryDecision.NotFound ->
            SetDeliveryError("Замовлення не знайдено")
        is SetDeliveryDecision.WrongStatus ->
            SetDeliveryError("Очікується DRAFT, поточний статус: ${decision.current}")
        is SetDeliveryDecision.BlankAddress ->
            SetDeliveryError(decision.message)
        is SetDeliveryDecision.BlankPhone ->
            SetDeliveryError(decision.message)
    }
}
