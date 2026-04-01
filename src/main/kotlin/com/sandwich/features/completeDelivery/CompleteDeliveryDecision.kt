package com.sandwich.features.completeDelivery

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class CompleteDeliveryInput(val order: Order)

// ── Рішення (результат чистої функції) ──

sealed interface CompleteDeliveryDecision {
    data class Delivered(val order: Order) : CompleteDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : CompleteDeliveryDecision
}

// ── Pure logic ──

fun completeDelivery(input: CompleteDeliveryInput): CompleteDeliveryDecision =
    when {
        input.order.status != OrderStatus.OUT_FOR_DELIVERY -> CompleteDeliveryDecision.WrongStatus(input.order.status)
        else -> CompleteDeliveryDecision.Delivered(input.order.copy(status = OrderStatus.DELIVERED))
    }
