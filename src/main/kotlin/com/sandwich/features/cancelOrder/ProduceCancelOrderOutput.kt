package com.sandwich.features.cancelOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.*
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceCancelOrderOutput(
    storeOrder: suspend (Order) -> Unit,
    releaseStock: suspend (Map<String, Int>) -> Unit
): suspend (CancelOrderDecision) -> CancelResponse = { decision ->
    when (decision) {
        is CancelOrderDecision.Cancelled -> {
            storeOrder(decision.order)
            if (decision.releaseStock.isNotEmpty()) {
                releaseStock(decision.releaseStock)
            }
            CancelResponse(
                orderId = decision.order.id,
                status = "CANCELLED",
                refund = decision.refund
            )
        }
        is CancelOrderDecision.AlreadyCancelled ->
            orderError(ALREADY_CANCELLED, "Замовлення вже скасовано")
        is CancelOrderDecision.TooLate ->
            orderError(TOO_LATE, "Неможливо скасувати — статус: ${decision.status}")
        is CancelOrderDecision.WindowExpired ->
            orderError(CANCEL_WINDOW_EXPIRED, "Вікно скасування (${decision.maxMinutes} хв) минуло")
    }
}
