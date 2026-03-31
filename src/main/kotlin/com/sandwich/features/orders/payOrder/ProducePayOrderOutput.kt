package com.sandwich.features.orders.payOrder

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProducePayOrderOutput(
    storeOrder: (Order) -> Unit,
    reduceStock: (Map<String, Int>) -> Unit
): suspend (PayOrderDecision) -> PayOrderResponse = { decision ->
    when (decision) {
        is PayOrderDecision.Paid -> {
            storeOrder(decision.order)
            reduceStock(decision.stockReductions)
            PayOrderResponse(
                orderId = decision.order.id,
                status = decision.order.status.name,
                paymentMethod = decision.order.payment!!.method
            )
        }
        is PayOrderDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is PayOrderDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується AWAITING_PAYMENT, поточний статус: ${decision.current}")
        is PayOrderDecision.OutOfStock ->
            orderError(OUT_OF_STOCK, "Немає в наявності: ${decision.unavailable}")
    }
}
