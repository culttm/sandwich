# End-to-End Examples

Complete sandwich implementations from the Sandwich project.

## 1. CreateOrder (full command slice)

### Domain.kt — pure types + logic

```kotlin
data class CreateOrderInput(
    val orderId: String,
    val customerName: String,
    val items: List<OrderItemRequest>,
    val menu: Map<String, MenuItem>,
    val extras: Map<String, ExtraItem>,
    val now: Instant
)

sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String = "Замовлення не може бути порожнім") : CreateOrderDecision
    data class BlankName(val message: String = "Вкажіть ім'я") : CreateOrderDecision
    data class UnknownSandwich(val ids: List<String>) : CreateOrderDecision
    data class UnknownExtras(val ids: List<String>) : CreateOrderDecision
}

fun buildOrder(input: CreateOrderInput): CreateOrderDecision {
    if (input.customerName.isBlank()) return CreateOrderDecision.BlankName()
    if (input.items.isEmpty()) return CreateOrderDecision.EmptyOrder()

    val unknownSandwiches = input.items.map { it.sandwichId }.filter { it !in input.menu }
    if (unknownSandwiches.isNotEmpty()) return CreateOrderDecision.UnknownSandwich(unknownSandwiches)

    val lines = input.items.map { item ->
        val sandwich = input.menu.getValue(item.sandwichId)
        val itemExtras = item.extras.map { input.extras.getValue(it) }
        val lineTotal = calculateLineTotal(sandwich.price, itemExtras.map { it.price })
        OrderLine(sandwichId = sandwich.id, extras = itemExtras, lineTotal = lineTotal)
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
    readMenu: () -> Map<String, MenuItem>,
    readExtras: () -> Map<String, ExtraItem>,
    generateId: () -> String,
    now: () -> Instant
): (CreateOrderRequest) -> CreateOrderInput = { request ->
    CreateOrderInput(
        orderId = generateId(),
        customerName = request.customerName,
        items = request.items,
        menu = readMenu(),
        extras = readExtras(),
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
        is CreateOrderDecision.BlankName -> orderError(BLANK_NAME, decision.message)
        is CreateOrderDecision.EmptyOrder -> orderError(EMPTY_ORDER, decision.message)
        is CreateOrderDecision.UnknownSandwich -> orderError(UNKNOWN_SANDWICH, "Невідомі: ${decision.ids}")
        is CreateOrderDecision.UnknownExtras -> orderError(UNKNOWN_EXTRAS, "Невідомі: ${decision.ids}")
    }
}
```

### CreateOrder.kt — route + wiring

```kotlin
fun Route.createOrderRoute(db: Db) = createOrderRoute(
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readMenu = { db.sandwiches.toMap() },
            readExtras = { db.extras.toMap() },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::buildOrder,
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
    val decision = buildOrder(input)
    assertIs<CreateOrderDecision.BlankName>(decision)
}

@Test
fun `valid order is created with discount`() {
    val input = createOrderInput(
        items = listOf(item("s1"), item("s2"), item("s3")),
        menu = mapOf("s1" to menuItem(100), "s2" to menuItem(150), "s3" to menuItem(200))
    )
    val decision = buildOrder(input)
    assertIs<CreateOrderDecision.Created>(decision)
    assertEquals(450, decision.order.subtotal)
    assertTrue(decision.order.discount > 0)
}
```

No mocks. No runTest. No coroutines. Just data in → decision out.

---

## 2. CancelOrder (with time-based logic)

### Domain.kt

```kotlin
data class CancelOrderInput(
    val order: Order?,
    val now: Instant
)

sealed interface CancelOrderDecision {
    data class Cancelled(val order: Order, val refund: Boolean, val releasedStock: Map<String, Int>) : CancelOrderDecision
    data object NotFound : CancelOrderDecision
    data class WrongStatus(val current: OrderStatus) : CancelOrderDecision
    data class TooLate(val message: String) : CancelOrderDecision
}

fun decideCancellation(input: CancelOrderInput): CancelOrderDecision {
    val order = input.order ?: return CancelOrderDecision.NotFound
    if (order.status == OrderStatus.CANCELLED) return CancelOrderDecision.WrongStatus(order.status)
    if (order.status == OrderStatus.DELIVERED) return CancelOrderDecision.TooLate("Already delivered")

    val refund = order.status in setOf(OrderStatus.PAID, OrderStatus.DISPATCHED)

    return CancelOrderDecision.Cancelled(
        order = order.copy(status = OrderStatus.CANCELLED),
        refund = refund,
        releasedStock = order.items.associate { it.sandwichId to 1 }
    )
}
```

**Key:** time-dependent logic receives `now` as input parameter, not `Instant.now()` call.

---

## 3. GetOrder (query slice — no sandwich needed)

```kotlin
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
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
│  │  buildOrder(input) → CreateOrderDecision          │   │
│  │  decideDelivery(input) → SetDeliveryDecision      │   │
│  │  decideCancellation(input) → CancelOrderDecision  │   │
│  │  calculateDeliveryFee(subtotal) → Int             │   │
│  │  calculateDiscount(count, subtotal) → Int         │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

**Hexagonal test:** can I test `buildOrder` without DB, without HTTP, without mocks?

```kotlin
val input = CreateOrderInput(items = listOf(...), menu = mapOf(...))
val decision = buildOrder(input)  // works — pure data in, decision out
```
