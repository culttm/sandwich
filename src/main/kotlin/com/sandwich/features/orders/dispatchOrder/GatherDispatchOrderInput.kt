package com.sandwich.features.orders.dispatchOrder

import com.sandwich.features.orders.Order

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherDispatchOrderInput(
    readOrder: (String) -> Order?
): (String) -> DispatchOrderInput = { orderId ->
    DispatchOrderInput(order = readOrder(orderId))
}
