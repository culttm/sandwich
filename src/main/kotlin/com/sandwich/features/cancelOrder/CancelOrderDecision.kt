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
    val order: Order,
    val now: Instant
)

// ── Рішення (результат чистої функції) ──

sealed interface CancelOrderDecision {
    data class Cancelled(
        val order: Order,
        val releaseStock: Map<String, Int>,  // sandwichId → кількість до повернення
        val refund: Boolean                  // чи потрібен refund
    ) : CancelOrderDecision
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

fun cancelOrder(input: CancelOrderInput): CancelOrderDecision =
    when {
        input.order.status == OrderStatus.CANCELLED -> CancelOrderDecision.AlreadyCancelled(input.order.id)
        input.order.status !in CANCELLABLE_STATUSES -> CancelOrderDecision.TooLate(input.order.status)
        minutesSinceCreation(input.order, input.now) > CANCEL_WINDOW_MINUTES -> CancelOrderDecision.WindowExpired(CANCEL_WINDOW_MINUTES)
        else -> {
            val wasPaid = input.order.payment != null
            CancelOrderDecision.Cancelled(
                order = input.order.copy(status = OrderStatus.CANCELLED),
                releaseStock = if (wasPaid) input.order.items.groupingBy { it.sandwichId }.eachCount() else emptyMap(),
                refund = wasPaid
            )
        }
    }

private fun minutesSinceCreation(order: Order, now: Instant): Long =
    Duration.between(Instant.parse(order.createdAt), now).toMinutes()
