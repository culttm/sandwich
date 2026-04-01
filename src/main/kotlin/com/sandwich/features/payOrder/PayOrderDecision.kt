package com.sandwich.features.payOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus
import com.sandwich.features.PaymentInfo
import com.sandwich.features.PaymentMethod
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class PayOrderInput(
    val order: Order,
    val stock: Map<String, Int>,
    val method: PaymentMethod,
    val now: Instant,
    val transactionId: String
)

// ── Рішення (результат чистої функції) ──

sealed interface PayOrderDecision {
    data class Paid(val order: Order, val stockReductions: Map<String, Int>) : PayOrderDecision
    data class WrongStatus(val current: OrderStatus) : PayOrderDecision
    data class OutOfStock(val unavailable: List<String>) : PayOrderDecision
}

// ── Pure logic ──

fun payOrder(input: PayOrderInput): PayOrderDecision {
    val required = input.order.items.groupingBy { it.sandwichId }.eachCount()
    val unavailable = required.filter { (id, qty) -> (input.stock[id] ?: 0) < qty }.keys.toList()

    return when {
        input.order.status != OrderStatus.AWAITING_PAYMENT -> PayOrderDecision.WrongStatus(input.order.status)
        unavailable.isNotEmpty() -> PayOrderDecision.OutOfStock(unavailable)
        else -> PayOrderDecision.Paid(
            order = input.order.copy(
                status = OrderStatus.PREPARING,
                payment = PaymentInfo(
                    method = input.method,
                    paidAt = input.now.toString(),
                    transactionId = input.transactionId
                )
            ),
            stockReductions = required
        )
    }
}
