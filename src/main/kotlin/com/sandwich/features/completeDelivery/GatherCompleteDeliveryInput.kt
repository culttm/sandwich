package com.sandwich.features.completeDelivery

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.ORDER_NOT_FOUND
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCompleteDeliveryInput(
    readOrder: suspend (String) -> Order?
): suspend (String) -> CompleteDeliveryInput = { orderId ->
    CompleteDeliveryInput(
        order = readOrder(orderId) ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
    )
}
