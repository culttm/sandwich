package com.sandwich.features.orders.cancelOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun CancelOrderHandler(
    gatherInput: (String) -> CancelOrderInput,
    decide: (CancelOrderInput) -> CancelDecision,
    produceOutput: suspend (CancelDecision) -> CancelResponse
): suspend (String) -> CancelResponse = { orderId ->
    val input = gatherInput(orderId)
    val decision = decide(input)
    produceOutput(decision)
}
