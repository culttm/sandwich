package com.sandwich.features.payOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.ORDER_NOT_FOUND
import com.sandwich.features.orderError
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherPayOrderInput(
    readOrder: suspend (String) -> Order?,
    readStock: suspend () -> Map<String, Int>,
    now: () -> Instant,
    generateTransactionId: () -> String
): suspend (String, PayOrderRequest) -> PayOrderInput = { orderId, request ->
    PayOrderInput(
        order = readOrder(orderId) ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено"),
        stock = readStock(),
        method = request.method,
        now = now(),
        transactionId = generateTransactionId()
    )
}
