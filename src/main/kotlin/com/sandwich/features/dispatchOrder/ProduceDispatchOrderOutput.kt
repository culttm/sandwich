package com.sandwich.features.dispatchOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.*
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceDispatchOrderOutput(
    storeOrder: suspend (Order) -> Unit
): suspend (DispatchOrderDecision) -> DispatchResponse = { decision ->
    when (decision) {
        is DispatchOrderDecision.Dispatched -> {
            storeOrder(decision.order)
            DispatchResponse(orderId = decision.order.id, status = "OUT_FOR_DELIVERY")
        }
        is DispatchOrderDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is DispatchOrderDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується PREPARING, поточний статус: ${decision.current}")
    }
}
