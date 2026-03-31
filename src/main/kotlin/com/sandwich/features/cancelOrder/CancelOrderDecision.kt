package com.sandwich.features.cancelOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus
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

sealed interface CancelOrderDecision {
    data class Cancelled(
        val order: Order,
        val releaseStock: Map<String, Int>,  // sandwichId → кількість до повернення
        val refund: Boolean                  // чи потрібен refund
    ) : CancelOrderDecision
    data object NotFound : CancelOrderDecision
    data class AlreadyCancelled(val orderId: String) : CancelOrderDecision
    data class TooLate(val status: OrderStatus) : CancelOrderDecision
    data class WindowExpired(val maxMinutes: Long) : CancelOrderDecision
}

// ── Pure logic ──

private const val CANCEL_WINDOW_MINUTES = 15L

private val CANCELLABLE_STATUSES = setOf(
    OrderStatus.DRAFT,
    OrderStatus.AWAITING_PAYMENT,
    OrderStatus.PREPARING
)

fun cancelOrder(input: CancelOrderInput): CancelOrderDecision {
    val order = input.order

    if (order == null)
        return CancelOrderDecision.NotFound

    if (order.status == OrderStatus.CANCELLED)
        return CancelOrderDecision.AlreadyCancelled(order.id)

    if (order.status !in CANCELLABLE_STATUSES)
        return CancelOrderDecision.TooLate(order.status)

    val elapsed = Duration.between(Instant.parse(order.createdAt), input.now)
    if (elapsed.toMinutes() > CANCEL_WINDOW_MINUTES)
        return CancelOrderDecision.WindowExpired(CANCEL_WINDOW_MINUTES)

    // Якщо замовлення було оплачене (PREPARING) → release stock + refund
    val wasPaid = order.payment != null
    val stockToRelease = if (wasPaid) {
        order.items.groupingBy { it.sandwichId }.eachCount()
    } else {
        emptyMap()
    }

    return CancelOrderDecision.Cancelled(
        order = order.copy(status = OrderStatus.CANCELLED),
        releaseStock = stockToRelease,
        refund = wasPaid
    )
}
