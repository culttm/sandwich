package com.sandwich.features.orders.completeDelivery

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class CompleteDeliveryInput(val order: Order?)

// ── Рішення (результат чистої функції) ──

sealed interface CompleteDeliveryDecision {
    data class Delivered(val order: Order) : CompleteDeliveryDecision
    data object NotFound : CompleteDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : CompleteDeliveryDecision
}

// ── Pure logic ──

fun completeDelivery(input: CompleteDeliveryInput): CompleteDeliveryDecision {
    val order = input.order

    if (order == null)
        return CompleteDeliveryDecision.NotFound

    if (order.status != OrderStatus.OUT_FOR_DELIVERY)
        return CompleteDeliveryDecision.WrongStatus(order.status)

    return CompleteDeliveryDecision.Delivered(
        order.copy(status = OrderStatus.DELIVERED)
    )
}
