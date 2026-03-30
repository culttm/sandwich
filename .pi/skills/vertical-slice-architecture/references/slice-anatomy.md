# Slice Anatomy

How to structure each individual slice based on its complexity.

## Level 1: Direct Query (simplest)

No business logic. Read data, return DTO. Single file.

```kotlin
// orders/getOrder/GetOrder.kt
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        call.respond(HttpStatusCode.OK, order.toResponse())
    }
}
```

**When:** GET endpoints, simple lookups.
**Contains:** Route + maybe a response mapping function. That's it.
**Tests:** E2E test through HTTP.

---

## Level 2: Transaction Script

Sequential steps without complex branching. Logic is straightforward enough
that sealed class decisions would be overkill. Single file.

```kotlin
// customers/createCustomer/CreateCustomer.kt
fun Route.createCustomerRoute(db: Db) {
    post("/customers") {
        val request = call.receive<CreateCustomerRequest>()
        if (request.name.isBlank()) orderError(BLANK_NAME, "Name required")
        val id = UUID.randomUUID().toString()
        db.customers[id] = Customer(id = id, name = request.name.trim())
        call.respond(HttpStatusCode.Created, CustomerResponse(id))
    }
}
```

**When:** simple CRUD, few validation rules, no branching outcomes.
**Contains:** Route + maybe small pure helpers.
**Tests:** E2E test through HTTP.

---

## Level 3: 3-Phase Sandwich (default for commands)

Business logic with branching outcomes. Full decomposition into 5 files.

### File structure

```
createOrder/
├── CreateOrder.kt              ← HTTP DTOs + route (wiring + protocol)
├── Domain.kt                   ← Input + Decision sealed interface + pure logic
├── CreateOrderHandler.kt       ← Orchestrator (3-line composition)
├── GatherCreateOrderInput.kt   ← 🔴 READ phase
└── ProduceCreateOrderOutput.kt ← 🔴 WRITE phase + error mapping
```

### CreateOrder.kt (entry point)

```kotlin
// ── HTTP DTOs ──
@Serializable data class CreateOrderRequest(val customerName: String, val items: List<OrderItemRequest>)
@Serializable data class CreateOrderResponse(val orderId: String, val total: Int)

// ── Route (wiring) ──
fun Route.createOrderRoute(db: Db) = createOrderRoute(
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readMenu = { db.sandwiches.toMap() },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::buildOrder,
        produceOutput = ProduceCreateOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
)

// ── Route (HTTP protocol) ──
fun Route.createOrderRoute(handler: suspend (CreateOrderRequest) -> CreateOrderResponse) {
    post("/orders") {
        val request = call.receive<CreateOrderRequest>()
        call.respond(HttpStatusCode.Created, handler(request))
    }
}
```

### Domain.kt (pure types + logic)

```kotlin
data class CreateOrderInput(
    val orderId: String,
    val customerName: String,
    val items: List<OrderItemRequest>,
    val menu: Map<String, MenuItem>,
    val now: Instant
)

sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String) : CreateOrderDecision
    data class BlankName(val message: String) : CreateOrderDecision
    data class UnknownSandwich(val ids: List<String>) : CreateOrderDecision
}

fun buildOrder(input: CreateOrderInput): CreateOrderDecision {
    if (input.customerName.isBlank()) return CreateOrderDecision.BlankName("Вкажіть ім'я")
    if (input.items.isEmpty()) return CreateOrderDecision.EmptyOrder("Замовлення порожнє")
    // ... pure logic ...
    return CreateOrderDecision.Created(order)
}
```

### Handler (trivial orchestrator)

```kotlin
fun CreateOrderHandler(
    gatherInput: (CreateOrderRequest) -> CreateOrderInput,
    decide: (CreateOrderInput) -> CreateOrderDecision,
    produceOutput: suspend (CreateOrderDecision) -> CreateOrderResponse
): suspend (CreateOrderRequest) -> CreateOrderResponse = { request ->
    val input = gatherInput(request)
    val decision = decide(input)
    produceOutput(decision)
}
```

### GatherInput (READ phase)

```kotlin
fun GatherCreateOrderInput(
    readMenu: () -> Map<String, MenuItem>,
    generateId: () -> String,
    now: () -> Instant
): (CreateOrderRequest) -> CreateOrderInput = { request ->
    CreateOrderInput(
        orderId = generateId(),
        customerName = request.customerName,
        items = request.items,
        menu = readMenu(),
        now = now()
    )
}
```

### ProduceOutput (WRITE phase + error mapping)

```kotlin
fun ProduceCreateOrderOutput(
    storeOrder: (Order) -> Unit
): suspend (CreateOrderDecision) -> CreateOrderResponse = { decision ->
    when (decision) {
        is CreateOrderDecision.Created -> {
            storeOrder(decision.order)
            CreateOrderResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is CreateOrderDecision.BlankName -> orderError(BLANK_NAME, decision.message)
        is CreateOrderDecision.EmptyOrder -> orderError(EMPTY_ORDER, decision.message)
        is CreateOrderDecision.UnknownSandwich -> orderError(UNKNOWN_SANDWICH, "Невідомі: ${decision.ids}")
    }
}
```

**When:** commands with business rules, multiple outcomes, non-trivial logic.
**Tests:** pure function tested with unit tests (no mocks, no runTest); full flow tested with E2E test.

---

## Choosing the Right Level

```
Does the slice have business logic?
├── NO → Level 1 (Direct Query) — single file
├── YES, but linear flow, no branching?
│   └── Level 2 (Transaction Script) — single file
└── YES, with branching outcomes?
    └── Level 3 (3-Phase Sandwich) — 5 files ← default for commands
```

## Test Strategy Per Level

| Level | Unit tests | E2E tests |
|---|---|---|
| 1. Direct Query | None (no pure logic) | HTTP request → response |
| 2. Transaction Script | Small pure helpers | HTTP request → response |
| 3. 3-Phase Sandwich | Pure function — all branches | Full checkout flow |

### Unit test template (pure function — no mocks)

```kotlin
@Test
fun `blank name is rejected`() {
    val input = createOrderInput(customerName = "  ")
    val decision = buildOrder(input)
    assertIs<CreateOrderDecision.BlankName>(decision)
}

@Test
fun `valid order calculates discount for 3+ items`() {
    val input = createOrderInput(items = listOf(item("s1"), item("s2"), item("s3")))
    val decision = buildOrder(input)
    assertIs<CreateOrderDecision.Created>(decision)
    assertTrue(decision.order.discount > 0)
}
```
