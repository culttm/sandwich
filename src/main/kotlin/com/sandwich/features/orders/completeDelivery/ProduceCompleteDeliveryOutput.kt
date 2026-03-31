package com.sandwich.features.orders.completeDelivery

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceCompleteDeliveryOutput(
    storeOrder: (Order) -> Unit
): suspend (CompleteDeliveryDecision) -> CompleteDeliveryResponse = { decision ->
    when (decision) {
        is CompleteDeliveryDecision.Delivered -> {
            storeOrder(decision.order)
            CompleteDeliveryResponse(orderId = decision.order.id, status = "DELIVERED")
        }
        is CompleteDeliveryDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is CompleteDeliveryDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується OUT_FOR_DELIVERY, поточний статус: ${decision.current}")
    }
}
