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
**Infrastructure concerns (not found, connection errors) are handled here — not in the pure function.**

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

**Key:** `order` is non-nullable in `AssignShippingInput`. The "not found" check lives in Gather
because it's an infrastructure concern (data missing from DB), not a business decision.

### Phase 2: decide (🟢 PURE)

Pure function. No `suspend`. Takes data, returns sealed decision.
Use `when` expressions for explicit, exhaustive control flow.

```kotlin
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

### Phase 3: ProduceOutput (🔴 WRITE)

Persists the decision + maps errors to typed exceptions.

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

### Handler: Pure Orchestrator

Composes the three phases. The handler itself is trivial — just sequencing.

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

### Wiring: Route as Composition Root

The route function wires real dependencies into lambdas:

```kotlin
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
```

## Error Handling: Typed Error Vocabulary

Errors modeled as shared enum with HTTP status mapping. StatusPages catches and converts:

```kotlin
enum class DomainErrorCode(val status: HttpStatusCode) {
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),
    BLANK_ADDRESS(HttpStatusCode.BadRequest),
}

data class DomainError(val code: DomainErrorCode, val message: String)
class DomainException(val error: DomainError) : Exception(error.message)
fun domainError(code: DomainErrorCode, message: String): Nothing =
    throw DomainException(DomainError(code, message))
```

### Where errors are thrown

| Error type | Where | Why |
|---|---|---|
| **Not Found** (data missing from DB) | GatherInput | Infrastructure concern — pure function shouldn't know about DB absence |
| **Business rule violation** (wrong status, blank field) | ProduceOutput (maps Decision) | Business decision made by pure function, mapped to error in WRITE phase |

ProduceOutput maps error decisions to `domainError()` calls — they never return, StatusPages catches.
GatherInput throws `domainError()` directly for infrastructure errors like "not found".

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
fun createOrder(
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
fun ProduceOutput(store: (Order) -> Unit): suspend (Decision) -> Response = { decision ->
    when (decision) {
        is Created -> {
            if (decision.order.total > 1000) { /* business rule here! */ }
            store(decision.order)
            // ...
        }
    }
}
```

### ✅ All logic in decide(), ProduceOutput only persists + maps errors
```kotlin
// RIGHT: ProduceOutput is dumb — store or throw
fun ProduceOutput(store: (Order) -> Unit): suspend (Decision) -> Response = { decision ->
    when (decision) {
        is Created -> {
            store(decision.order)
            Response(orderId = decision.order.id)
        }
        is Error -> domainError(ERROR_CODE, decision.message)
    }
}
```

### ❌ NotFound as a Decision variant
```kotlin
// WRONG: "not found" is infrastructure (data missing), not a business decision
sealed interface Decision {
    data class Updated(val order: Order) : Decision
    data object NotFound : Decision           // ← doesn't belong here
    data class WrongStatus(val s: OrderStatus) : Decision
}

fun decide(input: Input): Decision {
    val order = input.order ?: return Decision.NotFound  // ← null-check in pure function
    // ...
}
```

### ✅ NotFound handled in Gather, Input is non-nullable
```kotlin
// RIGHT: Gather fails fast, pure function works with clean data
data class Input(val order: Order, /* ... */)  // non-nullable

fun GatherInput(readOrder: suspend (String) -> Order?): suspend (String) -> Input = { id ->
    Input(order = readOrder(id) ?: domainError(NOT_FOUND, "Order not found"))
}

fun decide(input: Input): Decision = when {   // no null-check needed
    input.order.status != DRAFT -> Decision.WrongStatus(input.order.status)
    else -> Decision.Updated(input.order.copy(/* ... */))
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
8. Input types have **non-nullable** fields — "not found" handled in GatherInput, not in Decision
9. Decision sealed interfaces have **no `NotFound`** variant — that's an infrastructure concern
10. Pure functions use **`when` expressions** — not early returns — for explicit, exhaustive control flow
