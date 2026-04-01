package com.sandwich.features.dispatchOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.ORDER_NOT_FOUND
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherDispatchOrderInput(
    readOrder: suspend (String) -> Order?
): suspend (String) -> DispatchOrderInput = { orderId ->
    DispatchOrderInput(
        order = readOrder(orderId) ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
    )
}
