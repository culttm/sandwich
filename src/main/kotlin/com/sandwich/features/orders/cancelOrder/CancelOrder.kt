package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import com.sandwich.common.infra.OrderStore
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

// ══════════════════════════════════════════════════════════
//  Level 3: Impureim Sandwich (Recawr)
//  Скасування замовлення — є бізнес-правила → sealed decision
// ══════════════════════════════════════════════════════════

// ── Decision ──

sealed interface CancelDecision {
    data class Cancelled(val order: Order) : CancelDecision
    data object NotFound : CancelDecision
    data class AlreadyCancelled(val orderId: String) : CancelDecision
    data class TooLate(val status: OrderStatus) : CancelDecision
    data class WindowExpired(val maxMinutes: Long) : CancelDecision
}

// ── Pure logic (NOT suspend) ──

private const val CANCEL_WINDOW_MINUTES = 15L

fun decideCancellation(order: Order?, now: Instant): CancelDecision {
    if (order == null)
        return CancelDecision.NotFound

    if (order.status == OrderStatus.CANCELLED)
        return CancelDecision.AlreadyCancelled(order.id)

    if (order.status != OrderStatus.PENDING)
        return CancelDecision.TooLate(order.status)

    val elapsed = Duration.between(Instant.parse(order.createdAt), now)
    if (elapsed.toMinutes() > CANCEL_WINDOW_MINUTES)
        return CancelDecision.WindowExpired(CANCEL_WINDOW_MINUTES)

    return CancelDecision.Cancelled(order.copy(status = OrderStatus.CANCELLED))
}

// ── Response DTOs ──

@Serializable
data class CancelResponse(val orderId: String, val status: String)

@Serializable
data class CancelError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun CancelOrder(orderStore: OrderStore): suspend (String) -> Any = handler@{ orderId ->

    // 🔴 READ
    val order = orderStore.findById(orderId)
    val now = Instant.now()

    // 🟢 CALCULATE
    val decision = decideCancellation(order, now)

    // 🔴 WRITE
    when (decision) {
        is CancelDecision.Cancelled -> {
            orderStore.update(decision.order)
            CancelResponse(orderId = decision.order.id, status = "CANCELLED")
        }
        is CancelDecision.NotFound ->
            CancelError("Замовлення не знайдено")
        is CancelDecision.AlreadyCancelled ->
            CancelError("Замовлення вже скасовано")
        is CancelDecision.TooLate ->
            CancelError("Неможливо скасувати — статус: ${decision.status}")
        is CancelDecision.WindowExpired ->
            CancelError("Вікно скасування (${decision.maxMinutes} хв) минуло")
    }
}
