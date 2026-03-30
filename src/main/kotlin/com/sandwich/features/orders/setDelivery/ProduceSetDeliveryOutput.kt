package com.sandwich.features.orders.setDelivery

import com.sandwich.common.domain.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceSetDeliveryOutput(
    storeOrder: (Order) -> Unit
): suspend (SetDeliveryDecision) -> SetDeliveryResponse = { decision ->
    when (decision) {
        is SetDeliveryDecision.DeliverySet -> {
            storeOrder(decision.order)
            SetDeliveryResponse(
                orderId = decision.order.id,
                deliveryFee = decision.order.deliveryFee,
                total = decision.order.total
            )
        }
        is SetDeliveryDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is SetDeliveryDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується DRAFT, поточний статус: ${decision.current}")
        is SetDeliveryDecision.BlankAddress ->
            orderError(BLANK_ADDRESS, decision.message)
        is SetDeliveryDecision.BlankPhone ->
            orderError(BLANK_PHONE, decision.message)
    }
}
