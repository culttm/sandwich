package com.sandwich.features.completeDelivery

import com.sandwich.features.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCompleteDeliveryInput(
    readOrder: (String) -> Order?
): (String) -> CompleteDeliveryInput = { orderId ->
    CompleteDeliveryInput(order = readOrder(orderId))
}
