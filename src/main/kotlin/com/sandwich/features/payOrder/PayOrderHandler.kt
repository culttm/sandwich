package com.sandwich.features.payOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun PayOrderHandler(
    gatherInput: suspend (String, PayOrderRequest) -> PayOrderInput,
    decide: (PayOrderInput) -> PayOrderDecision,
    produceOutput: suspend (PayOrderDecision) -> PayOrderResponse
): suspend (String, PayOrderRequest) -> PayOrderResponse = { orderId, request ->
    val input = gatherInput(orderId, request)
    val decision = decide(input)
    produceOutput(decision)
}
