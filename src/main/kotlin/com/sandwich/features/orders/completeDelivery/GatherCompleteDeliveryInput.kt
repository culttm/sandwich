package com.sandwich.features.orders.completeDelivery

import com.sandwich.common.domain.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCompleteDeliveryInput(
    readOrder: (String) -> Order?
): (String) -> CompleteDeliveryInput = { orderId ->
    CompleteDeliveryInput(order = readOrder(orderId))
}
