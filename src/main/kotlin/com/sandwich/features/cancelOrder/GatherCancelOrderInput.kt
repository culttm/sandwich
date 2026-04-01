package com.sandwich.features.cancelOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.ORDER_NOT_FOUND
import com.sandwich.features.orderError
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCancelOrderInput(
    readOrder: suspend (String) -> Order?,
    now: () -> Instant
): suspend (String) -> CancelOrderInput = { orderId ->
    CancelOrderInput(
        order = readOrder(orderId) ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено"),
        now = now()
    )
}
