package com.sandwich.features.dispatchOrder

import com.sandwich.features.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherDispatchOrderInput(
    readOrder: suspend (String) -> Order?
): suspend (String) -> DispatchOrderInput = { orderId ->
    DispatchOrderInput(order = readOrder(orderId))
}
