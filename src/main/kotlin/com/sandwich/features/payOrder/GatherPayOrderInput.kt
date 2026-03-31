package com.sandwich.features.payOrder

import com.sandwich.features.Order
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
        order = readOrder(orderId),
        stock = readStock(),
        method = request.method,
        now = now(),
        transactionId = generateTransactionId()
    )
}
