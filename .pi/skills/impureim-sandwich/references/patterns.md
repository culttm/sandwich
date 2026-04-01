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
assignShipping/
├── AssignShipping.kt              ← HTTP DTOs + route (wiring)
├── Domain.kt                      ← Input type + Decision sealed interface + pure logic
├── AssignShippingHandler.kt       ← Orchestrator (3-line composition)
├── GatherAssignShippingInput.kt   ← READ phase
└── ProduceAssignShippingOutput.kt ← WRITE phase
```

### Domain.kt — pure types + logic

```kotlin
// ── Input (assembled by GatherInput) ──
// Note: order is non-nullable — "not found" is handled in GatherInput
data class AssignShippingInput(
    val order: Order,
    val address: String,
    val phone: String,
    val deliveryDate: String?
)

// ── Decision (sealed — each branch = different outcome) ──
// Note: no NotFound variant — that's an infrastructure concern, not a business decision
sealed interface AssignShippingDecision {
    data class ShippingAssigned(val order: Order) : AssignShippingDecision
    data class WrongStatus(val current: OrderStatus) : AssignShippingDecision
    data object BlankAddress : AssignShippingDecision
}

// ── Pure logic (NOT suspend, when expression) ──
fun assignShipping(input: AssignShippingInput): AssignShippingDecision =
    when {
        input.order.status != OrderStatus.DRAFT -> AssignShippingDecision.WrongStatus(input.order.status)
        input.address.isBlank() -> AssignShippingDecision.BlankAddress
        else -> {
            val shippingFee = calculateShippingFee(input.order.subtotal)
            AssignShippingDecision.ShippingAssigned(
                input.order.copy(status = OrderStatus.AWAITING_PAYMENT, shippingFee = shippingFee)
            )
        }
    }
```

### Handler — trivial orchestrator

```kotlin
fun AssignShippingHandler(
    gatherInput: (String, AssignShippingRequest) -> AssignShippingInput,
    decide: (AssignShippingInput) -> AssignShippingDecision,
    produceOutput: suspend (AssignShippingDecision) -> AssignShippingResponse
): suspend (String, AssignShippingRequest) -> AssignShippingResponse = { orderId, request ->
    val input = gatherInput(orderId, request)
    val decision = decide(input)
    produceOutput(decision)
}
```

### GatherInput — READ phase

```kotlin
fun GatherAssignShippingInput(
    readOrder: suspend (String) -> Order?
): suspend (String, AssignShippingRequest) -> AssignShippingInput = { orderId, request ->
    AssignShippingInput(
        order = readOrder(orderId) ?: domainError(ORDER_NOT_FOUND, "Order not found"),
        address = request.address,
        phone = request.phone,
        deliveryDate = request.deliveryDate
    )
}
```

**Key:** `NotFound` is handled here in Gather — not in the Decision function.
The pure function receives a non-nullable `Order` and focuses only on business rules.

### ProduceOutput — WRITE phase + error mapping

```kotlin
fun ProduceAssignShippingOutput(
    storeOrder: suspend (Order) -> Unit
): suspend (AssignShippingDecision) -> AssignShippingResponse = { decision ->
    when (decision) {
        is AssignShippingDecision.ShippingAssigned -> {
            storeOrder(decision.order)
            AssignShippingResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is AssignShippingDecision.WrongStatus ->
            domainError(WRONG_STATUS, "Expected DRAFT, got: ${decision.current}")
        is AssignShippingDecision.BlankAddress ->
            domainError(BLANK_ADDRESS, "Address is required")
    }
}
```

### Route — wiring (composition root)

```kotlin
// Wiring: connects real deps to phases
fun Route.assignShippingRoute(db: Db) = assignShippingRoute(
    AssignShippingHandler(
        gatherInput = GatherAssignShippingInput(
            readOrder = { id -> db.orders[id] }
        ),
        decide = ::assignShipping,
        produceOutput = ProduceAssignShippingOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
)

// HTTP protocol: receives request, calls handler, responds
fun Route.assignShippingRoute(
    handler: suspend (String, AssignShippingRequest) -> AssignShippingResponse
) {
    post("/orders/{id}/shipping") {
        val id = call.parameters["id"]!!
        val request = call.receive<AssignShippingRequest>()
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
// Prefer `when` expression — explicit, exhaustive control flow.
fun decide(input: SomeInput): SomeDecision =
    when {
        condition1 -> SomeDecision.OptionA(...)
        condition2 -> SomeDecision.OptionB(...)
        else -> SomeDecision.OptionC(...)
    }

// Input = everything the pure function needs
// Fields are non-nullable — Gather handles "not found" before calling decide
data class SomeInput(
    val existingData: ExistingData,      // from DB (non-nullable — Gather fails fast if missing)
    val requestData: String,             // from HTTP request
    val now: Instant                     // from clock (passed in, not called)
)

// Decision = all possible BUSINESS outcomes
// No NotFound — that's infrastructure, handled in Gather
sealed interface SomeDecision {
    data class Success(val result: DomainObject) : SomeDecision
    data class ValidationError(val message: String) : SomeDecision
}
```

### When a function has complex happy-path logic

Extract the happy path into a private function:

```kotlin
fun createOrder(input: CreateOrderInput): CreateOrderDecision {
    val unknownProducts = input.items.filter { it.id !in input.catalog }

    return when {
        input.name.isBlank() -> CreateOrderDecision.BlankName()
        input.items.isEmpty() -> CreateOrderDecision.EmptyOrder()
        unknownProducts.isNotEmpty() -> CreateOrderDecision.UnknownProducts(unknownProducts)
        else -> buildOrder(input)  // complex logic extracted
    }
}

private fun buildOrder(input: CreateOrderInput): CreateOrderDecision.Created {
    val lines = input.items.map { /* ... */ }
    val subtotal = lines.sumOf { it.total }
    return CreateOrderDecision.Created(Order(items = lines, subtotal = subtotal))
}
```
