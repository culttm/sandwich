package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Скасування замовлення
//  DRAFT / AWAITING_PAYMENT → просто скасовуємо
//  PREPARING                → скасовуємо + refund + release stock
//  OUT_FOR_DELIVERY / DELIVERED → занадто пізно
// ══════════════════════════════════════════════════════════════

// ── Decision ──

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

// ── Pure logic (NOT suspend) ──

private const val CANCEL_WINDOW_MINUTES = 15L

private val CANCELLABLE_STATUSES = setOf(
    OrderStatus.DRAFT,
    OrderStatus.AWAITING_PAYMENT,
    OrderStatus.PREPARING
)

fun decideCancellation(order: Order?, now: Instant): CancelDecision {
    if (order == null)
        return CancelDecision.NotFound

    if (order.status == OrderStatus.CANCELLED)
        return CancelDecision.AlreadyCancelled(order.id)

    if (order.status !in CANCELLABLE_STATUSES)
        return CancelDecision.TooLate(order.status)

    val elapsed = Duration.between(Instant.parse(order.createdAt), now)
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

// ── Response DTOs ──

@Serializable
data class CancelResponse(val orderId: String, val status: String, val refund: Boolean)

@Serializable
data class CancelError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun CancelOrder(db: Db): suspend (String) -> Any = handler@{ orderId ->

    // 🔴 READ
    val order = db.orders[orderId]
    val now = Instant.now()

    // 🟢 CALCULATE
    val decision = decideCancellation(order, now)

    // 🔴 WRITE
    when (decision) {
        is CancelDecision.Cancelled -> {
            db.orders[decision.order.id] = decision.order
            decision.releaseStock.forEach { (id, qty) ->
                db.stock.compute(id) { _, current -> (current ?: 0) + qty }
            }
            CancelResponse(
                orderId = decision.order.id,
                status = "CANCELLED",
                refund = decision.refund
            )
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
