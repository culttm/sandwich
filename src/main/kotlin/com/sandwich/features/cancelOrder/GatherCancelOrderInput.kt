package com.sandwich.features.cancelOrder

import com.sandwich.features.Order
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCancelOrderInput(
    readOrder: suspend (String) -> Order?,
    now: () -> Instant
): suspend (String) -> CancelOrderInput = { orderId ->
    CancelOrderInput(
        order = readOrder(orderId),
        now = now()
    )
}
