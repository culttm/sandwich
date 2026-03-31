package com.sandwich.features.completeDelivery

import com.sandwich.features.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCompleteDeliveryInput(
    readOrder: suspend (String) -> Order?
): suspend (String) -> CompleteDeliveryInput = { orderId ->
    CompleteDeliveryInput(order = readOrder(orderId))
}
