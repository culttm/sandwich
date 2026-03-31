package com.sandwich.features.orders.setDelivery

import com.sandwich.features.orders.DeliveryInfo
import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class SetDeliveryInput(
    val order: Order?,
    val address: String,
    val phone: String,
    val deliveryTime: String?
)

// ── Рішення (результат чистої функції) ──

sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data object NotFound : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
    data class BlankAddress(val message: String = "Вкажіть адресу") : SetDeliveryDecision
    data class BlankPhone(val message: String = "Вкажіть телефон") : SetDeliveryDecision
}

// ── Pure logic ──

fun setDelivery(input: SetDeliveryInput): SetDeliveryDecision {
    val order = input.order

    if (order == null)
        return SetDeliveryDecision.NotFound

    if (order.status != OrderStatus.DRAFT)
        return SetDeliveryDecision.WrongStatus(order.status)

    if (input.address.isBlank())
        return SetDeliveryDecision.BlankAddress()

    if (input.phone.isBlank())
        return SetDeliveryDecision.BlankPhone()

    val deliveryFee = calculateDeliveryFee(order.subtotal)
    val delivery = DeliveryInfo(
        address = input.address.trim(),
        phone = input.phone.trim(),
        deliveryTime = input.deliveryTime,
        deliveryFee = deliveryFee
    )

    return SetDeliveryDecision.DeliverySet(
        order.copy(
            status = OrderStatus.AWAITING_PAYMENT,
            delivery = delivery,
            deliveryFee = deliveryFee,
            total = order.subtotal - order.discount + deliveryFee
        )
    )
}
