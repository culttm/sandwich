package com.sandwich.features.setDelivery

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.ORDER_NOT_FOUND
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherSetDeliveryInput(
    readOrder: suspend (String) -> Order?
): suspend (String, SetDeliveryRequest) -> SetDeliveryInput = { orderId, request ->
    SetDeliveryInput(
        order = readOrder(orderId) ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено"),
        address = request.address,
        phone = request.phone,
        deliveryTime = request.deliveryTime
    )
}
