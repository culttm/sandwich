package com.sandwich.features.orders.dispatchOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class DispatchOrderInput(val order: Order?)

// ── Рішення (результат чистої функції) ──

sealed interface DispatchDecision {
    data class Dispatched(val order: Order) : DispatchDecision
    data object NotFound : DispatchDecision
    data class WrongStatus(val current: OrderStatus) : DispatchDecision
}

// ── Pure logic ──

fun decideDispatch(input: DispatchOrderInput): DispatchDecision {
    val order = input.order

    if (order == null)
        return DispatchDecision.NotFound

    if (order.status != OrderStatus.PREPARING)
        return DispatchDecision.WrongStatus(order.status)

    return DispatchDecision.Dispatched(
        order.copy(status = OrderStatus.OUT_FOR_DELIVERY)
    )
}
