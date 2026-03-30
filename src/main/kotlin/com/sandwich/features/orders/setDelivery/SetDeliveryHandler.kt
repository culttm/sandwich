package com.sandwich.features.orders.setDelivery

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun SetDeliveryHandler(
    gatherInput: (String, SetDeliveryRequest) -> SetDeliveryInput,
    decide: (SetDeliveryInput) -> SetDeliveryDecision,
    produceOutput: suspend (SetDeliveryDecision) -> SetDeliveryResponse
): suspend (String, SetDeliveryRequest) -> SetDeliveryResponse = { orderId, request ->
    val input = gatherInput(orderId, request)
    val decision = decide(input)
    produceOutput(decision)
}
