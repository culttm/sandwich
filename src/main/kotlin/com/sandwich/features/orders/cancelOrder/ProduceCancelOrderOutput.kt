package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.Order
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceCancelOrderOutput(
    storeOrder: (Order) -> Unit,
    releaseStock: (Map<String, Int>) -> Unit
): suspend (CancelDecision) -> CancelResponse = { decision ->
    when (decision) {
        is CancelDecision.Cancelled -> {
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
        is CancelDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is CancelDecision.AlreadyCancelled ->
            orderError(ALREADY_CANCELLED, "Замовлення вже скасовано")
        is CancelDecision.TooLate ->
            orderError(TOO_LATE, "Неможливо скасувати — статус: ${decision.status}")
        is CancelDecision.WindowExpired ->
            orderError(CANCEL_WINDOW_EXPIRED, "Вікно скасування (${decision.maxMinutes} хв) минуло")
    }
}
