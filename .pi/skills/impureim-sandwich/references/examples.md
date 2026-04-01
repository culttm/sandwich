# End-to-End Examples

Complete sandwich implementations showing the pattern at different complexity levels.

## 1. CreateOrder (full command slice)

### Domain.kt — pure types + logic

```kotlin
data class CreateOrderInput(
    val orderId: String,
    val customerName: String,
    val items: List<OrderItemRequest>,
    val catalog: Map<String, Product>,
    val now: Instant
)

sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String = "Order must have items") : CreateOrderDecision
    data class BlankName(val message: String = "Customer name is required") : CreateOrderDecision
    data class UnknownProducts(val ids: List<String>) : CreateOrderDecision
}

// when expression — validation branches + extracted happy path
fun createOrder(input: CreateOrderInput): CreateOrderDecision {
    val unknownIds = input.items.map { it.productId }.filter { it !in input.catalog }

    return when {
        input.customerName.isBlank() -> CreateOrderDecision.BlankName()
        input.items.isEmpty() -> CreateOrderDecision.EmptyOrder()
        unknownIds.isNotEmpty() -> CreateOrderDecision.UnknownProducts(unknownIds)
        else -> buildOrder(input)
    }
}

private fun buildOrder(input: CreateOrderInput): CreateOrderDecision.Created {
    val lines = input.items.map { item ->
        val product = input.catalog.getValue(item.productId)
        val lineTotal = calculateLineTotal(product.price, item.quantity)
        OrderLine(productId = product.id, quantity = item.quantity, lineTotal = lineTotal)
    }

    val subtotal = lines.sumOf { it.lineTotal }
    val discount = calculateDiscount(lines.size, subtotal)

    return CreateOrderDecision.Created(
        Order(id = input.orderId, items = lines, subtotal = subtotal, discount = discount,
              total = subtotal - discount, status = OrderStatus.DRAFT, createdAt = input.now.toString())
    )
}
```

### GatherCreateOrderInput.kt

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

### CreateOrderHandler.kt

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

### ProduceCreateOrderOutput.kt

```kotlin
fun ProduceCreateOrderOutput(
    storeOrder: (Order) -> Unit
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

### CreateOrder.kt — route + wiring

```kotlin
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

fun Route.createOrderRoute(handler: suspend (CreateOrderRequest) -> CreateOrderResponse) {
    post("/orders") {
        val request = call.receive<CreateOrderRequest>()
        call.respond(HttpStatusCode.Created, handler(request))
    }
}
```

### Tests — pure logic only, no mocks

```kotlin
@Test
fun `blank name is rejected`() {
    val input = createOrderInput(customerName = "  ")
    val decision = createOrder(input)
    assertIs<CreateOrderDecision.BlankName>(decision)
}

@Test
fun `valid order is created with discount`() {
    val input = createOrderInput(
        items = listOf(item("p1"), item("p2"), item("p3")),
        catalog = mapOf("p1" to product(100), "p2" to product(150), "p3" to product(200))
    )
    val decision = createOrder(input)
    assertIs<CreateOrderDecision.Created>(decision)
    assertEquals(450, decision.order.subtotal)
    assertTrue(decision.order.discount > 0)
}
```

No mocks. No runTest. No coroutines. Just data in → decision out.

---

## 2. CancelOrder (with status-based logic)

### Domain.kt

```kotlin
// order is non-nullable — Gather handles "not found"
data class CancelOrderInput(
    val order: Order,
    val now: Instant
)

// No NotFound variant — that's handled in Gather
sealed interface CancelOrderDecision {
    data class Cancelled(val order: Order, val refund: Boolean) : CancelOrderDecision
    data class WrongStatus(val current: OrderStatus) : CancelOrderDecision
    data class TooLate(val message: String) : CancelOrderDecision
}

fun cancelOrder(input: CancelOrderInput): CancelOrderDecision =
    when {
        input.order.status == OrderStatus.CANCELLED -> CancelOrderDecision.WrongStatus(input.order.status)
        input.order.status == OrderStatus.DELIVERED -> CancelOrderDecision.TooLate("Already delivered")
        else -> CancelOrderDecision.Cancelled(
            order = input.order.copy(status = OrderStatus.CANCELLED),
            refund = input.order.status in setOf(OrderStatus.PAID, OrderStatus.DISPATCHED)
        )
    }
```

### GatherCancelOrderInput.kt

```kotlin
fun GatherCancelOrderInput(
    readOrder: suspend (String) -> Order?,
    now: () -> Instant
): suspend (String) -> CancelOrderInput = { orderId ->
    CancelOrderInput(
        order = readOrder(orderId) ?: domainError(ORDER_NOT_FOUND, "Order not found"),
        now = now()
    )
}
```

**Key:** time-dependent logic receives `now` as input parameter, not `Instant.now()` call.
**Key:** "not found" handled in Gather with `?: domainError(...)`, not as a Decision variant.

---

## 3. GetOrder (query slice — no sandwich needed)

```kotlin
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: domainError(ORDER_NOT_FOUND, "Order not found")
        call.respond(HttpStatusCode.OK, order.toResponse())
    }
}
```

Query slices don't need the 3-phase decomposition — they're simple reads.

---

## Architecture Visualization

```
┌────────────────────────────────────────────────────────┐
│  Route (composition root)                               │
│  Wires: db lambdas → GatherInput, ProduceOutput        │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Handler (pure orchestrator)                     │   │
│  │  gatherInput(request) → decide(input) → produce  │   │
│  │                                                  │   │
│  │  GatherInput ─────→ Domain ──────→ ProduceOutput │   │
│  │   🔴 READ           🟢 PURE         🔴 WRITE    │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Pure Core (Domain.kt)                           │   │
│  │                                                  │   │
│  │  createOrder(input) → CreateOrderDecision          │   │
│  │  assignShipping(input) → AssignShippingDecision   │   │
│  │  cancelOrder(input) → CancelOrderDecision  │   │
│  │  calculateShippingFee(subtotal) → Int             │   │
│  │  calculateDiscount(count, subtotal) → Int         │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

**Hexagonal test:** can I test `createOrder` without DB, without HTTP, without mocks?

```kotlin
val input = CreateOrderInput(items = listOf(...), catalog = mapOf(...))
val decision = createOrder(input)  // works — pure data in, decision out
```
