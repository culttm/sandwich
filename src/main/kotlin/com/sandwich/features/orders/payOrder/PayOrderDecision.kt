package com.sandwich.features.orders.payOrder

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderStatus
import com.sandwich.features.orders.PaymentInfo
import com.sandwich.features.orders.PaymentMethod
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class PayOrderInput(
    val order: Order?,
    val stock: Map<String, Int>,
    val method: PaymentMethod,
    val now: Instant,
    val transactionId: String
)

// ── Рішення (результат чистої функції) ──

sealed interface PayOrderDecision {
    data class Paid(val order: Order, val stockReductions: Map<String, Int>) : PayOrderDecision
    data object NotFound : PayOrderDecision
    data class WrongStatus(val current: OrderStatus) : PayOrderDecision
    data class OutOfStock(val unavailable: List<String>) : PayOrderDecision
}

// ── Pure logic ──

fun payOrder(input: PayOrderInput): PayOrderDecision {
    val order = input.order

    if (order == null)
        return PayOrderDecision.NotFound

    if (order.status != OrderStatus.AWAITING_PAYMENT)
        return PayOrderDecision.WrongStatus(order.status)

    val required = order.items
        .groupingBy { it.sandwichId }
        .eachCount()

    val unavailable = required
        .filter { (id, qty) -> (input.stock[id] ?: 0) < qty }
        .keys.toList()

    if (unavailable.isNotEmpty())
        return PayOrderDecision.OutOfStock(unavailable)

    val payment = PaymentInfo(
        method = input.method,
        paidAt = input.now.toString(),
        transactionId = input.transactionId
    )

    return PayOrderDecision.Paid(
        order = order.copy(
            status = OrderStatus.PREPARING,
            payment = payment
        ),
        stockReductions = required
    )
}
