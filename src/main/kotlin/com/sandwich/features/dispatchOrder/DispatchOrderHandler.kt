package com.sandwich.features.dispatchOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun DispatchOrderHandler(
    gatherInput: suspend (String) -> DispatchOrderInput,
    decide: (DispatchOrderInput) -> DispatchOrderDecision,
    produceOutput: suspend (DispatchOrderDecision) -> DispatchResponse
): suspend (String) -> DispatchResponse = { orderId ->
    val input = gatherInput(orderId)
    val decision = decide(input)
    produceOutput(decision)
}
