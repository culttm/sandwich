package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import java.time.Duration
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class CancelOrderInput(
    val order: Order?,
    val now: Instant
)

// ── Рішення (результат чистої функції) ──

sealed interface CancelDecision {
    data class Cancelled(
        val order: Order,
        val releaseStock: Map<String, Int>,  // sandwichId → кількість до повернення
        val refund: Boolean                  // чи потрібен refund
    ) : CancelDecision
    data object NotFound : CancelDecision
    data class AlreadyCancelled(val orderId: String) : CancelDecision
    data class TooLate(val status: OrderStatus) : CancelDecision
    data class WindowExpired(val maxMinutes: Long) : CancelDecision
}

// ── Pure logic ──

private const val CANCEL_WINDOW_MINUTES = 15L

private val CANCELLABLE_STATUSES = setOf(
    OrderStatus.DRAFT,
    OrderStatus.AWAITING_PAYMENT,
    OrderStatus.PREPARING
)

fun decideCancellation(input: CancelOrderInput): CancelDecision {
    val order = input.order

    if (order == null)
        return CancelDecision.NotFound

    if (order.status == OrderStatus.CANCELLED)
        return CancelDecision.AlreadyCancelled(order.id)

    if (order.status !in CANCELLABLE_STATUSES)
        return CancelDecision.TooLate(order.status)

    val elapsed = Duration.between(Instant.parse(order.createdAt), input.now)
    if (elapsed.toMinutes() > CANCEL_WINDOW_MINUTES)
        return CancelDecision.WindowExpired(CANCEL_WINDOW_MINUTES)

    // Якщо замовлення було оплачене (PREPARING) → release stock + refund
    val wasPaid = order.payment != null
    val stockToRelease = if (wasPaid) {
        order.items.groupingBy { it.sandwichId }.eachCount()
    } else {
        emptyMap()
    }

    return CancelDecision.Cancelled(
        order = order.copy(status = OrderStatus.CANCELLED),
        releaseStock = stockToRelease,
        refund = wasPaid
    )
}
