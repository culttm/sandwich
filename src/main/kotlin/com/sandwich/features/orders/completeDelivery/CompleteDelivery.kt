package com.sandwich.features.orders.completeDelivery

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Крок 5: Кур'єр доставив → DELIVERED
// ══════════════════════════════════════════════════════════════

// ── Decision ──

sealed interface CompleteDeliveryDecision {
    data class Delivered(val order: Order) : CompleteDeliveryDecision
    data object NotFound : CompleteDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : CompleteDeliveryDecision
}

// ── Pure logic (NOT suspend) ──

fun decideComplete(order: Order?): CompleteDeliveryDecision {
    if (order == null)
        return CompleteDeliveryDecision.NotFound

    if (order.status != OrderStatus.OUT_FOR_DELIVERY)
        return CompleteDeliveryDecision.WrongStatus(order.status)

    return CompleteDeliveryDecision.Delivered(
        order.copy(status = OrderStatus.DELIVERED)
    )
}

// ── Response DTOs ──

@Serializable
data class CompleteDeliveryResponse(val orderId: String, val status: String)

@Serializable
data class CompleteDeliveryError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun CompleteDelivery(db: Db): suspend (String) -> Any = handler@{ orderId ->

    // 🔴 READ
    val order = db.orders[orderId]

    // 🟢 CALCULATE
    val decision = decideComplete(order)

    // 🔴 WRITE
    when (decision) {
        is CompleteDeliveryDecision.Delivered -> {
            db.orders[decision.order.id] = decision.order
            CompleteDeliveryResponse(orderId = decision.order.id, status = "DELIVERED")
        }
        is CompleteDeliveryDecision.NotFound ->
            CompleteDeliveryError("Замовлення не знайдено")
        is CompleteDeliveryDecision.WrongStatus ->
            CompleteDeliveryError("Очікується OUT_FOR_DELIVERY, поточний статус: ${decision.current}")
    }
}
