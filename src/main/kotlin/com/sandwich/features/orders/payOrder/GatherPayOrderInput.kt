package com.sandwich.features.orders.payOrder

import com.sandwich.features.orders.Order
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherPayOrderInput(
    readOrder: (String) -> Order?,
    readStock: () -> Map<String, Int>,
    now: () -> Instant,
    generateTransactionId: () -> String
): (String, PayOrderRequest) -> PayOrderInput = { orderId, request ->
    PayOrderInput(
        order = readOrder(orderId),
        stock = readStock(),
        method = request.method,
        now = now(),
        transactionId = generateTransactionId()
    )
}
