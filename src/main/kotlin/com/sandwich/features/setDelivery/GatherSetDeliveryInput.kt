package com.sandwich.features.setDelivery

import com.sandwich.features.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherSetDeliveryInput(
    readOrder: (String) -> Order?
): (String, SetDeliveryRequest) -> SetDeliveryInput = { orderId, request ->
    SetDeliveryInput(
        order = readOrder(orderId),
        address = request.address,
        phone = request.phone,
        deliveryTime = request.deliveryTime
    )
}
