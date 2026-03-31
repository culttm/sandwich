package com.sandwich.features.dispatchOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class DispatchOrderInput(val order: Order?)

// ── Рішення (результат чистої функції) ──

sealed interface DispatchOrderDecision {
    data class Dispatched(val order: Order) : DispatchOrderDecision
    data object NotFound : DispatchOrderDecision
    data class WrongStatus(val current: OrderStatus) : DispatchOrderDecision
}

// ── Pure logic ──

fun dispatchOrder(input: DispatchOrderInput): DispatchOrderDecision {
    val order = input.order

    if (order == null)
        return DispatchOrderDecision.NotFound

    if (order.status != OrderStatus.PREPARING)
        return DispatchOrderDecision.WrongStatus(order.status)

    return DispatchOrderDecision.Dispatched(
        order.copy(status = OrderStatus.OUT_FOR_DELIVERY)
    )
}
