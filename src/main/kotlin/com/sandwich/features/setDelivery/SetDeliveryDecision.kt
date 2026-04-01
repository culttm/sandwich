package com.sandwich.features.setDelivery

import com.sandwich.features.DeliveryInfo
import com.sandwich.features.Order
import com.sandwich.features.OrderStatus

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class SetDeliveryInput(
    val order: Order,
    val address: String,
    val phone: String,
    val deliveryTime: String?
)

// ── Рішення (результат чистої функції) ──

sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
    data class BlankAddress(val message: String = "Вкажіть адресу") : SetDeliveryDecision
    data class BlankPhone(val message: String = "Вкажіть телефон") : SetDeliveryDecision
}

// ── Pure logic ──

fun setDelivery(input: SetDeliveryInput): SetDeliveryDecision {
    return when {
        input.order.status != OrderStatus.DRAFT -> SetDeliveryDecision.WrongStatus(input.order.status)
        input.address.isBlank() -> SetDeliveryDecision.BlankAddress()
        input.phone.isBlank() -> SetDeliveryDecision.BlankPhone()
        else -> {
            val deliveryFee = calculateDeliveryFee(input.order.subtotal)
            SetDeliveryDecision.DeliverySet(
                input.order.copy(
                    status = OrderStatus.AWAITING_PAYMENT,
                    delivery = DeliveryInfo(
                        address = input.address.trim(),
                        phone = input.phone.trim(),
                        deliveryTime = input.deliveryTime,
                        deliveryFee = deliveryFee
                    ),
                    deliveryFee = deliveryFee,
                    total = input.order.subtotal - input.order.discount + deliveryFee
                )
            )
        }
    }
}
