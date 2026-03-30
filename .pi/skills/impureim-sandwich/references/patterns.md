# Sandwich Patterns

Three levels of the pattern, from ideal to realistic.

## 1. Ideal Impureim Sandwich (3-layer)

```
🔴 Impure — collect data
🟢 Pure   — business logic
🔴 Impure — write result
```

**When to use:** Simple operations with one read source and straightforward write.

## 2. 3-Phase Decomposition (default for commands)

Each phase is a separate, named, testable function:

```
GatherInput    →    decide    →    ProduceOutput
  🔴 READ          🟢 PURE          🔴 WRITE
  (collects)       (decides)        (persists + maps errors)
```

### File structure per slice

```
setDelivery/
├── SetDelivery.kt              ← HTTP DTOs + route (wiring)
├── Domain.kt                   ← Input type + Decision sealed interface + pure logic
├── SetDeliveryHandler.kt       ← Orchestrator (3-line composition)
├── GatherSetDeliveryInput.kt   ← READ phase
└── ProduceSetDeliveryOutput.kt ← WRITE phase
```

### Domain.kt — pure types + logic

```kotlin
// ── Input (assembled by GatherInput) ──
data class SetDeliveryInput(
    val order: Order?,
    val address: String,
    val phone: String,
    val deliveryTime: String?
)

// ── Decision (sealed — each branch = different outcome) ──
sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data object NotFound : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
    data class BlankAddress(val message: String = "Вкажіть адресу") : SetDeliveryDecision
}

// ── Pure logic (NOT suspend) ──
fun decideDelivery(input: SetDeliveryInput): SetDeliveryDecision {
    val order = input.order ?: return SetDeliveryDecision.NotFound
    if (order.status != OrderStatus.DRAFT) return SetDeliveryDecision.WrongStatus(order.status)
    if (input.address.isBlank()) return SetDeliveryDecision.BlankAddress()

    val deliveryFee = calculateDeliveryFee(order.subtotal)
    return SetDeliveryDecision.DeliverySet(
        order.copy(status = OrderStatus.AWAITING_PAYMENT, deliveryFee = deliveryFee)
    )
}
```

### Handler — trivial orchestrator

```kotlin
fun SetDeliveryHandler(
    gatherInput: (String, SetDeliveryRequest) -> SetDeliveryInput,
    decide: (SetDeliveryInput) -> SetDeliveryDecision,
    produceOutput: suspend (SetDeliveryDecision) -> SetDeliveryResponse
): suspend (String, SetDeliveryRequest) -> SetDeliveryResponse = { orderId, request ->
    val input = gatherInput(orderId, request)
    val decision = decide(input)
    produceOutput(decision)
}
```

### GatherInput — READ phase

```kotlin
fun GatherSetDeliveryInput(
    readOrder: (String) -> Order?
): (String, SetDeliveryRequest) -> SetDeliveryInput = { orderId, request ->
    SetDeliveryInput(
        order = readOrder(orderId),
        address = request.address,
        phone = request.phone,
        deliveryTime = request.deliveryTime
    )
}
```

### ProduceOutput — WRITE phase + error mapping

```kotlin
fun ProduceSetDeliveryOutput(
    storeOrder: (Order) -> Unit
): suspend (SetDeliveryDecision) -> SetDeliveryResponse = { decision ->
    when (decision) {
        is SetDeliveryDecision.DeliverySet -> {
            storeOrder(decision.order)
            SetDeliveryResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is SetDeliveryDecision.NotFound ->
            orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        is SetDeliveryDecision.WrongStatus ->
            orderError(WRONG_STATUS, "Очікується DRAFT, поточний: ${decision.current}")
        is SetDeliveryDecision.BlankAddress ->
            orderError(BLANK_ADDRESS, decision.message)
    }
}
```

### Route — wiring (composition root)

```kotlin
// Wiring: connects real deps to phases
fun Route.setDeliveryRoute(db: Db) = setDeliveryRoute(
    SetDeliveryHandler(
        gatherInput = GatherSetDeliveryInput(
            readOrder = { id -> db.orders[id] }
        ),
        decide = ::decideDelivery,
        produceOutput = ProduceSetDeliveryOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
)

// HTTP protocol: receives request, calls handler, responds
fun Route.setDeliveryRoute(handler: suspend (String, SetDeliveryRequest) -> SetDeliveryResponse) {
    post("/orders/{id}/delivery") {
        val id = call.parameters["id"]!!
        val request = call.receive<SetDeliveryRequest>()
        call.respond(HttpStatusCode.OK, handler(id, request))
    }
}
```

**When to use:** All command slices. This is the default.

## 3. Recawr Sandwich (Read → Calculate → Write)

A specialization with a stricter constraint:

> Once you start WRITING, you must NOT go back to READING new data.

The 3-Phase Decomposition naturally enforces Recawr because GatherInput runs
completely before ProduceOutput.

```
┌──────────────────────────────────────┐
│  Impureim Sandwiches (general)       │
│  ┌────────────────────────────────┐  │
│  │  3-Phase Decomposition        │  │
│  │  ┌──────────────────────────┐ │  │
│  │  │  Recawr (strictest)      │ │  │
│  │  └──────────────────────────┘ │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

## Choosing a Pattern

```
Can I gather all data before logic?
├── YES → 3-Phase Decomposition (GatherInput → decide → ProduceOutput)
├── Need cascading IO? → Consider splitting into multiple sandwiches
└── Simple query, no logic? → Skip sandwich, direct read (query slice)
```

## Pure Function Template

Every pure function in the sandwich follows this shape:

```kotlin
// NOT suspend. Takes Input data class. Returns sealed Decision.
fun decide(input: SomeInput): SomeDecision {
    // all logic here — no IO, no suspend, no side effects
    return when {
        condition1 -> SomeDecision.OptionA(...)
        condition2 -> SomeDecision.OptionB(...)
        else -> SomeDecision.OptionC(...)
    }
}

// Input = everything the pure function needs
data class SomeInput(
    val existingData: ExistingData?,    // from DB (nullable = might not exist)
    val requestData: String,             // from HTTP request
    val now: Instant                     // from clock (passed in, not called)
)

// Decision = all possible outcomes
sealed interface SomeDecision {
    data class Success(val result: DomainObject) : SomeDecision
    data class ValidationError(val message: String) : SomeDecision
    data object NotFound : SomeDecision
}
```
