package com.sandwich.features.orders.cancelOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun CancelOrderHandler(
    gatherInput: (String) -> CancelOrderInput,
    decide: (CancelOrderInput) -> CancelOrderDecision,
    produceOutput: suspend (CancelOrderDecision) -> CancelResponse
): suspend (String) -> CancelResponse = { orderId ->
    val input = gatherInput(orderId)
    val decision = decide(input)
    produceOutput(decision)
}
