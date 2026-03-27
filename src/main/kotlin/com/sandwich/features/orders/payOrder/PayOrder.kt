package com.sandwich.features.orders.payOrder

import com.sandwich.common.domain.*
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Крок 3: Оплата + резервація stock → PREPARING
// ══════════════════════════════════════════════════════════════

// ── Request DTO ──

@Serializable
data class PayOrderRequest(
    val method: PaymentMethod
)

// ── Decision ──

sealed interface PayOrderDecision {
    data class Paid(val order: Order, val stockReductions: Map<String, Int>) : PayOrderDecision
    data object NotFound : PayOrderDecision
    data class WrongStatus(val current: OrderStatus) : PayOrderDecision
    data class OutOfStock(val unavailable: List<String>) : PayOrderDecision
}

// ── Pure logic (NOT suspend) ──

fun decidePayment(
    order: Order?,
    stock: Map<String, Int>,
    method: PaymentMethod,
    now: Instant,
    transactionId: String
): PayOrderDecision {
    if (order == null)
        return PayOrderDecision.NotFound

    if (order.status != OrderStatus.AWAITING_PAYMENT)
        return PayOrderDecision.WrongStatus(order.status)

    // Підрахувати скільки кожного сендвіча потрібно
    val required = order.items
        .groupingBy { it.sandwichId }
        .eachCount()

    val unavailable = required
        .filter { (id, qty) -> (stock[id] ?: 0) < qty }
        .keys.toList()

    if (unavailable.isNotEmpty())
        return PayOrderDecision.OutOfStock(unavailable)

    val payment = PaymentInfo(
        method = method,
        paidAt = now.toString(),
        transactionId = transactionId
    )

    return PayOrderDecision.Paid(
        order = order.copy(
            status = OrderStatus.PREPARING,
            payment = payment
        ),
        stockReductions = required
    )
}

// ── Response DTOs ──

@Serializable
data class PayOrderResponse(
    val orderId: String,
    val status: String,
    val paymentMethod: PaymentMethod
)

@Serializable
data class PayOrderError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun PayOrder(db: Db): suspend (String, PayOrderRequest) -> Any = handler@{ orderId, request ->

    // 🔴 READ
    val order = db.orders[orderId]
    val stock = db.stock.toMap()
    val now = Instant.now()
    val transactionId = UUID.randomUUID().toString()

    // 🟢 CALCULATE
    val decision = decidePayment(order, stock, request.method, now, transactionId)

    // 🔴 WRITE
    when (decision) {
        is PayOrderDecision.Paid -> {
            db.orders[decision.order.id] = decision.order
            decision.stockReductions.forEach { (id, qty) ->
                db.stock.compute(id) { _, current -> (current ?: 0) - qty }
            }
            PayOrderResponse(
                orderId = decision.order.id,
                status = decision.order.status.name,
                paymentMethod = decision.order.payment!!.method
            )
        }
        is PayOrderDecision.NotFound ->
            PayOrderError("Замовлення не знайдено")
        is PayOrderDecision.WrongStatus ->
            PayOrderError("Очікується AWAITING_PAYMENT, поточний статус: ${decision.current}")
        is PayOrderDecision.OutOfStock ->
            PayOrderError("Немає в наявності: ${decision.unavailable}")
    }
}
