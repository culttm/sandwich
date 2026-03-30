package com.sandwich.features.orders.dispatchOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun DispatchOrderHandler(
    gatherInput: (String) -> DispatchOrderInput,
    decide: (DispatchOrderInput) -> DispatchDecision,
    produceOutput: suspend (DispatchDecision) -> DispatchResponse
): suspend (String) -> DispatchResponse = { orderId ->
    val input = gatherInput(orderId)
    val decision = decide(input)
    produceOutput(decision)
}
