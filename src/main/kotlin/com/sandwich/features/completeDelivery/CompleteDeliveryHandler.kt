package com.sandwich.features.completeDelivery

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun CompleteDeliveryHandler(
    gatherInput: (String) -> CompleteDeliveryInput,
    decide: (CompleteDeliveryInput) -> CompleteDeliveryDecision,
    produceOutput: suspend (CompleteDeliveryDecision) -> CompleteDeliveryResponse
): suspend (String) -> CompleteDeliveryResponse = { orderId ->
    val input = gatherInput(orderId)
    val decision = decide(input)
    produceOutput(decision)
}
