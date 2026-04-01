# Slice Anatomy

How to structure each individual slice based on its complexity.

## Level 1: Direct Query (simplest)

No business logic. Read data, return DTO. Single file.

```kotlin
// orders/getOrder/GetOrder.kt
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: domainError(ORDER_NOT_FOUND, "Order not found")
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
        if (request.name.isBlank()) domainError(BLANK_NAME, "Name required")
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
            readCatalog = { db.products.toMap() },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::createOrder,
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
    val catalog: Map<String, Product>,
    val now: Instant
)

// No NotFound — infrastructure concern, handled in Gather
sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String) : CreateOrderDecision
    data class BlankName(val message: String) : CreateOrderDecision
    data class UnknownProducts(val ids: List<String>) : CreateOrderDecision
}

// when expression — explicit control flow
fun createOrder(input: CreateOrderInput): CreateOrderDecision {
    val unknownIds = input.items.map { it.productId }.filter { it !in input.catalog }
    return when {
        input.customerName.isBlank() -> CreateOrderDecision.BlankName("Name required")
        input.items.isEmpty() -> CreateOrderDecision.EmptyOrder("Order must have items")
        unknownIds.isNotEmpty() -> CreateOrderDecision.UnknownProducts(unknownIds)
        else -> buildOrder(input)
    }
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
    readCatalog: suspend () -> Map<String, Product>,
    generateId: () -> String,
    now: () -> Instant
): suspend (CreateOrderRequest) -> CreateOrderInput = { request ->
    CreateOrderInput(
        orderId = generateId(),
        customerName = request.customerName,
        items = request.items,
        catalog = readCatalog(),
        now = now()
    )
}
```

For slices that load an existing entity:
```kotlin
fun GatherAssignShippingInput(
    readOrder: suspend (String) -> Order?
): suspend (String, Request) -> AssignShippingInput = { orderId, request ->
    AssignShippingInput(
        // NotFound handled HERE — pure function gets non-nullable Order
        order = readOrder(orderId) ?: domainError(ORDER_NOT_FOUND, "Order not found"),
        address = request.address
    )
}
```

### ProduceOutput (WRITE phase + error mapping)

```kotlin
fun ProduceCreateOrderOutput(
    storeOrder: suspend (Order) -> Unit
): suspend (CreateOrderDecision) -> CreateOrderResponse = { decision ->
    when (decision) {
        is CreateOrderDecision.Created -> {
            storeOrder(decision.order)
            CreateOrderResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is CreateOrderDecision.BlankName -> domainError(BLANK_NAME, decision.message)
        is CreateOrderDecision.EmptyOrder -> domainError(EMPTY_ORDER, decision.message)
        is CreateOrderDecision.UnknownProducts -> domainError(UNKNOWN_PRODUCT, "Unknown: ${decision.ids}")
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
| 3. 3-Phase Sandwich | Pure function — all branches | Full flow |

### Unit test template (pure function — no mocks)

```kotlin
@Test
fun `blank name is rejected`() {
    val input = createOrderInput(customerName = "  ")
    val decision = createOrder(input)
    assertIs<CreateOrderDecision.BlankName>(decision)
}

@Test
fun `valid order calculates discount for 3+ items`() {
    val input = createOrderInput(items = listOf(item("p1"), item("p2"), item("p3")))
    val decision = createOrder(input)
    assertIs<CreateOrderDecision.Created>(decision)
    assertTrue(decision.order.discount > 0)
}
```

**Note:** No `null order returns NotFound` tests — those are replaced by
Gather-level testing (E2E). Pure function tests focus on **business decisions** only.
