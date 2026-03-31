package com.sandwich.features.createOrder

// ══════════════════════════════════════════════════════════════
//  Оркестратор: композиція 3 фаз (READ → CALCULATE → WRITE)
// ══════════════════════════════════════════════════════════════

fun CreateOrderHandler(
    gatherInput: (CreateOrderRequest) -> CreateOrderInput,
    decide: (CreateOrderInput) -> CreateOrderDecision,
    produceOutput: suspend (CreateOrderDecision) -> CreateOrderResponse
): suspend (CreateOrderRequest) -> CreateOrderResponse = { request ->
    val input = gatherInput(request)
    val decision = decide(input)
    produceOutput(decision)
}
