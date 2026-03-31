package com.sandwich.features.orders.dispatchOrder

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceDispatchOrderOutput(
    storeOrder: (Order) -> Unit
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
