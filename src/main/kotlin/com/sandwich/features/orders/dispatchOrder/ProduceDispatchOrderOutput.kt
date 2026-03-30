package com.sandwich.features.orders.dispatchOrder

import com.sandwich.common.domain.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceDispatchOrderOutput(
    storeOrder: (Order) -> Unit
): suspend (DispatchDecision) -> DispatchResponse = { decision ->
    when (decision) {
        is DispatchDecision.Dispatched -> {
            storeOrder(decision.order)
            DispatchResponse(orderId = decision.order.id, status = "OUT_FOR_DELIVERY")
        }
        is DispatchDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is DispatchDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується PREPARING, поточний статус: ${decision.current}")
    }
}
