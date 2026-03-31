package com.sandwich.features.orders.cancelOrder

import com.sandwich.features.orders.Order
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCancelOrderInput(
    readOrder: (String) -> Order?,
    now: () -> Instant
): (String) -> CancelOrderInput = { orderId ->
    CancelOrderInput(
        order = readOrder(orderId),
        now = now()
    )
}
