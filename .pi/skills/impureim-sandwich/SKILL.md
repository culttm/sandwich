---
name: impureim-sandwich
description: >
  Functional architecture with Impureim/Recawr Sandwich pattern for Kotlin.
  Use when writing, reviewing, or refactoring Kotlin backend code —
  structuring pure/impure separation, modeling decisions with sealed classes,
  replacing DI with dependency rejection, or handling suspend on the edge.
---

# Impureim Sandwich

Structure Kotlin backend code as pure core + impure shell.

## The One Rule

> **Functional Interaction Law:** a pure function CANNOT call an impure action.

|  | Callee: Impure | Callee: Pure |
|---|---|---|
| **Caller: Impure** | ✅ | ✅ |
| **Caller: Pure** | ❌ | ✅ |

## Kotlin Impurity Markers

| Impure sign | Pure alternative |
|---|---|
| `suspend fun` | `fun` (non-suspend) |
| `LocalDateTime.now()` / `Instant.now()` | Pass `now` as argument |
| `UUID.randomUUID()` | Generate on edge, pass as argument |
| `Random.nextInt()` | Pass seed or result as argument |
| `repository.read(...)` / `repository.write(...)` | Pass result as argument |
| `httpClient.get(...)` | Call on edge, pass result as argument |
| `logger.info(...)` | Log only on edge |
| `transaction(db) { }` | Only on edge |
| `System.getenv("KEY")` | Read config at startup, pass as value |

Impurity is **transitive**: if `f` calls impure `g`, then `f` is impure too — up the entire call stack.

## Canonical Sandwich: 3-Phase Decomposition

Each command slice decomposes into three explicit phases:

```
GatherInput  →  decide  →  ProduceOutput
   🔴 READ      🟢 PURE      🔴 WRITE
```

### Phase 1: GatherInput (🔴 READ)

Collects everything the pure function needs. Returns a plain data object.

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

### Phase 2: decide (🟢 PURE)

Pure function. No `suspend`. Takes data, returns sealed decision.

```kotlin
fun decideDelivery(input: SetDeliveryInput): SetDeliveryDecision {
    val order = input.order
        ?: return SetDeliveryDecision.NotFound
    if (order.status != OrderStatus.DRAFT)
        return SetDeliveryDecision.WrongStatus(order.status)
    if (input.address.isBlank())
        return SetDeliveryDecision.BlankAddress()

    val deliveryFee = calculateDeliveryFee(order.subtotal)
    return SetDeliveryDecision.DeliverySet(
        order.copy(status = OrderStatus.AWAITING_PAYMENT, deliveryFee = deliveryFee)
    )
}
```

### Phase 3: ProduceOutput (🔴 WRITE)

Persists the decision + maps errors to typed exceptions.

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
    }
}
```

### Handler: Pure Orchestrator

Composes the three phases. The handler itself is trivial — just sequencing.

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

### Wiring: Route as Composition Root

The route function wires real dependencies into lambdas:

```kotlin
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
```

## Error Handling: Typed Error Vocabulary

Errors modeled as shared enum with HTTP status mapping. StatusPages catches and converts:

```kotlin
enum class OrderErrorCode(val status: HttpStatusCode) {
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),
    BLANK_ADDRESS(HttpStatusCode.BadRequest),
}

data class OrderError(val code: OrderErrorCode, val message: String)
class OrderException(val error: OrderError) : Exception(error.message)
fun orderError(code: OrderErrorCode, message: String): Nothing =
    throw OrderException(OrderError(code, message))
```

ProduceOutput maps error decisions to `orderError()` calls — they never return, StatusPages catches.

## Decision Tree

### Writing new code
→ Use 3-phase decomposition: GatherInput → decide → ProduceOutput
→ See [patterns.md](references/patterns.md)

### Refactoring existing code
→ Extract pure logic from class with DI → dependency rejection
→ See [refactoring.md](references/refactoring.md)

### Reviewing code for purity
→ Scan for impurity markers, check transitivity, verify suspend containment
→ See [purity-checklist.md](references/purity-checklist.md)

### Need a concrete example
→ See [examples.md](references/examples.md)

## Anti-Patterns

### ❌ Curried impure ≠ pure
```kotlin
// WRONG: looks FP but is impure — checkStock goes to DB inside
fun BuildOrder(
    checkStock: suspend (List<ProductId>) -> Map<ProductId, Int>
): suspend (Request) -> Decision = { request ->
    val stock = checkStock(request.productIds())  // impure inside!
    // ...
}
```

### ✅ Pass values, not functions
```kotlin
// RIGHT: accepts data, returns decision
fun buildOrder(
    stock: Map<ProductId, Int>,
    request: Request
): Decision { /* pure */ }
```

### ❌ Write before Calculate
```kotlin
// WRONG: writing results before calculating the full picture
items.map { repo.update(it) }  // write first!
val result = summarize(results) // calculate after — too late
```

### ✅ Calculate before Write
```kotlin
val (toUpdate, invalid) = partition(items, existing) // calculate first
toUpdate.forEach { repo.update(it) }                 // write after
```

### ❌ Decision logic in ProduceOutput
```kotlin
// WRONG: business rule inside the WRITE phase
fun ProduceOutput(storeOrder: (Order) -> Unit): suspend (Decision) -> Response = { decision ->
    when (decision) {
        is Created -> {
            if (decision.order.total > 1000) { /* business rule here! */ }
            storeOrder(decision.order)
            // ...
        }
    }
}
```

### ✅ All logic in decide(), ProduceOutput only persists + maps errors
```kotlin
// RIGHT: ProduceOutput is dumb — store or throw
fun ProduceOutput(storeOrder: (Order) -> Unit): suspend (Decision) -> Response = { decision ->
    when (decision) {
        is Created -> {
            storeOrder(decision.order)
            Response(orderId = decision.order.id)
        }
        is Error -> orderError(ERROR_CODE, decision.message)
    }
}
```

## Key Principle

> **Dependency Rejection**: instead of injecting a function that fetches data, fetch the data on the edge and pass the result to a pure function.

```
OOP:  bind Repository → call inside logic → impure
FP:   call Repository on edge → pass data to pure logic → testable
```

## Quality Check

Before finishing any implementation, verify:
1. All business logic functions are `fun` (not `suspend fun`)
2. No impurity markers inside pure functions
3. `suspend` only appears in GatherInput, ProduceOutput, and the handler's return type
4. Decisions modeled as sealed class/interface
5. Pure functions testable with plain data (no mocks, no runTest)
6. Handler is trivial 3-line composition: gather → decide → produce
7. ProduceOutput contains NO business logic — only persistence + error mapping
