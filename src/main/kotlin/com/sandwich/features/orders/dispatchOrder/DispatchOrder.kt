package com.sandwich.features.orders.dispatchOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Крок 4: Відправити кур'єром → OUT_FOR_DELIVERY
// ══════════════════════════════════════════════════════════════

// ── Decision ──

sealed interface DispatchDecision {
    data class Dispatched(val order: Order) : DispatchDecision
    data object NotFound : DispatchDecision
    data class WrongStatus(val current: OrderStatus) : DispatchDecision
}

// ── Pure logic (NOT suspend) ──

fun decideDispatch(order: Order?): DispatchDecision {
    if (order == null)
        return DispatchDecision.NotFound

    if (order.status != OrderStatus.PREPARING)
        return DispatchDecision.WrongStatus(order.status)

    return DispatchDecision.Dispatched(
        order.copy(status = OrderStatus.OUT_FOR_DELIVERY)
    )
}

// ── Response DTOs ──

@Serializable
data class DispatchResponse(val orderId: String, val status: String)

@Serializable
data class DispatchError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun DispatchOrder(db: Db): suspend (String) -> Any = handler@{ orderId ->

    // 🔴 READ
    val order = db.orders[orderId]

    // 🟢 CALCULATE
    val decision = decideDispatch(order)

    // 🔴 WRITE
    when (decision) {
        is DispatchDecision.Dispatched -> {
            db.orders[decision.order.id] = decision.order
            DispatchResponse(orderId = decision.order.id, status = "OUT_FOR_DELIVERY")
        }
        is DispatchDecision.NotFound ->
            DispatchError("Замовлення не знайдено")
        is DispatchDecision.WrongStatus ->
            DispatchError("Очікується PREPARING, поточний статус: ${decision.current}")
    }
}
