---
name: vertical-slice-architecture
description: >
  Vertical Slice Architecture for Kotlin backend projects.
  Use when creating new features, organizing project structure by use cases,
  deciding where shared logic lives, reviewing slice boundaries,
  or refactoring from layered architecture to feature-first organization.
---

# Vertical Slice Architecture

Organize code by feature, not by technical layer.

## The One Rule

> **Minimize coupling between slices, maximize coupling within a slice.**

```
Layered:   Controller → Service → Repository  (horizontal coupling)
VSA:       [CreateOrder slice] ←→ [SetDelivery slice]  (independent)
```

## Slice = Use Case

Each slice handles **one request** — either a command (write) or a query (read).

| Slice type | Contains | Example |
|---|---|---|
| **Command** (write) | HTTP DTOs, route, Domain.kt, Handler, GatherInput, ProduceOutput | CreateOrder, CancelOrder, SetDelivery |
| **Query** (read) | Response DTO, route (often no pure logic needed) | GetOrder, GetMenu |

## Canonical Command Slice (with Impureim Sandwich inside)

### File structure — 5 files per command slice

```
setDelivery/
├── SetDelivery.kt              ← HTTP DTOs + route (wiring + protocol)
├── Domain.kt                   ← Input type + Decision sealed interface + pure logic
├── SetDeliveryHandler.kt       ← Orchestrator (3-line composition)
├── GatherSetDeliveryInput.kt   ← 🔴 READ phase
└── ProduceSetDeliveryOutput.kt ← 🔴 WRITE phase + error mapping
```

### SetDelivery.kt — slice entry point

```kotlin
// ── HTTP DTOs ──
@Serializable
data class SetDeliveryRequest(val address: String, val phone: String)

@Serializable
data class SetDeliveryResponse(val orderId: String, val deliveryFee: Int, val total: Int)

// ── Route (wiring) — composition root ──
fun Route.setDeliveryRoute(db: Db) = setDeliveryRoute(
    SetDeliveryHandler(
        gatherInput = GatherSetDeliveryInput(readOrder = { id -> db.orders[id] }),
        decide = ::decideDelivery,
        produceOutput = ProduceSetDeliveryOutput(storeOrder = { order -> db.orders[order.id] = order })
    )
)

// ── Route (HTTP protocol) ──
fun Route.setDeliveryRoute(handler: suspend (String, SetDeliveryRequest) -> SetDeliveryResponse) {
    post("/orders/{id}/delivery") {
        val id = call.parameters["id"]!!
        val request = call.receive<SetDeliveryRequest>()
        call.respond(HttpStatusCode.OK, handler(id, request))
    }
}
```

**Two route overloads:**
1. **Wiring** — connects real deps to handler phases
2. **HTTP protocol** — receives/responds, knows nothing about business logic

### Domain.kt — pure types + logic

```kotlin
data class SetDeliveryInput(val order: Order?, val address: String, val phone: String)

sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data object NotFound : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
}

fun decideDelivery(input: SetDeliveryInput): SetDeliveryDecision { /* pure */ }
```

### Query slice (simple — no sandwich needed)

```kotlin
// getOrder/GetOrder.kt — single file
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено")
        call.respond(HttpStatusCode.OK, order.toResponse())
    }
}
```

**Key:** each slice picks its own approach. No forced uniformity.

## Project Structure

```
src/main/kotlin/com/sandwich/
├── apps/
│   └── SandwichHttpApi.kt          ← composition root (route registration)
├── features/
│   ├── menu/
│   │   └── getMenu/
│   │       └── GetMenu.kt          ← query slice (single file)
│   └── orders/
│       ├── OrderError.kt           ← shared error vocabulary
│       ├── createOrder/
│       │   ├── CreateOrder.kt       ← HTTP DTOs + route
│       │   ├── Domain.kt            ← pure types + logic
│       │   ├── CreateOrderHandler.kt
│       │   ├── GatherCreateOrderInput.kt
│       │   └── ProduceCreateOrderOutput.kt
│       ├── setDelivery/
│       │   ├── SetDelivery.kt
│       │   ├── Domain.kt
│       │   ├── SetDeliveryHandler.kt
│       │   ├── GatherSetDeliveryInput.kt
│       │   └── ProduceSetDeliveryOutput.kt
│       ├── payOrder/
│       │   └── ...                   ← same pattern
│       ├── dispatchOrder/
│       │   └── ...
│       ├── completeDelivery/
│       │   └── ...
│       ├── cancelOrder/
│       │   └── ...
│       └── getOrder/
│           └── GetOrder.kt          ← query slice (single file)
└── common/
    ├── domain/
    │   ├── PricingRules.kt          ← pure business rules
    │   └── Types.kt                 ← shared domain types (Order, OrderStatus, etc.)
    ├── http/
    │   ├── ErrorHandling.kt         ← StatusPages config
    │   └── HttpServer.kt            ← Ktor server setup
    ├── infra/
    │   └── Db.kt                    ← in-memory store
    └── app/
        └── App.kt                   ← lifecycle
```

## Composition Root: SandwichHttpApi.kt

Routes registered directly — no intermediate `Routing.kt`:

```kotlin
fun SandwichHttpApi(db: Db = Db().apply { seed() }) = App {
    val server = HttpServer(8080) {
        configureSerialization()
        configureErrorHandling()
        configureMonitoring()
        routing {
            getMenuRoute(db)
            createOrderRoute(db)
            getOrderRoute(db)
            setDeliveryRoute(db)
            payOrderRoute(db)
            dispatchOrderRoute(db)
            completeDeliveryRoute(db)
            cancelOrderRoute(db)
        }
    }
    server.start()
    Teardown { server.stop(1000L, 1000L) }
}
```

## Error Handling: Shared Error Vocabulary

All order slices share `OrderError` — a typed enum mapping errors to HTTP statuses:

```kotlin
enum class OrderErrorCode(val status: HttpStatusCode) {
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),
    BLANK_ADDRESS(HttpStatusCode.BadRequest),
    // ...each slice adds its codes
}

fun orderError(code: OrderErrorCode, message: String): Nothing =
    throw OrderException(OrderError(code, message))
```

StatusPages catches `OrderException` and converts to HTTP response automatically.
ProduceOutput functions call `orderError()` for error decisions — they throw, never return.

## Decision Tree

### Creating a new feature
→ Create new slice folder with 5 files (command) or 1 file (query)
→ Choose approach based on complexity: [slice-anatomy.md](references/slice-anatomy.md)

### Deciding where shared logic lives
→ Follow the extraction rules and litmus tests
→ See [shared-logic.md](references/shared-logic.md)

### Organizing or restructuring a project
→ Apply feature-first project structure
→ See [structure.md](references/structure.md)

### Reviewing code for VSA compliance
→ Check slice independence, common/ health, consistency
→ See [pitfalls.md](references/pitfalls.md)

## Anti-Patterns

### ❌ Layered architecture in a feature folder

```
features/orders/
├── domain/Order.kt
├── application/OrderService.kt      ← service layer is back!
├── infrastructure/OrderRepoImpl.kt  ← DI + interface + impl!
└── presentation/OrderController.kt  ← controller layer!
```

### ✅ True vertical slice

```
orders/createOrder/
├── CreateOrder.kt    ← HTTP DTOs + route
├── Domain.kt         ← pure types + logic
├── CreateOrderHandler.kt
├── GatherCreateOrderInput.kt
└── ProduceCreateOrderOutput.kt
```

### ❌ Slice calls another slice

```kotlin
fun CancelOrder(...) = { request ->
    val refund = SetDelivery(db)(request.orderId, reverseRequest)  // ← calling another slice!
}
```

### ✅ Extract shared pure logic instead

```kotlin
// common/domain/PricingRules.kt
fun calculateDeliveryFee(subtotal: Int): Int { /* pure */ }
// Both slices use the shared pure function
```

### ❌ common/ becomes a service layer

```kotlin
// common/services/InventoryService.kt ← 🚩
class InventoryService(private val repo: InventoryRepository) {
    suspend fun checkAndReserve(...) { ... }
}
```

### ✅ common/ contains only pure functions and infrastructure

```kotlin
// common/domain/PricingRules.kt ← ✅
fun calculateLineTotal(sandwichPrice: Int, extraPrices: List<Int>): Int { /* pure */ }
```

## VSA + Impureim Sandwich

VSA answers: **where** does the code live? (by feature)
Impureim Sandwich answers: **how** is the code structured inside? (3-phase decomposition)

```
┌───────────────────────────────────────────┐
│  VSA — organization by feature            │
│  ┌─────────────────────────────────────┐  │
│  │  Impureim Sandwich — logic inside   │  │
│  │  GatherInput → decide → ProduceOut  │  │
│  └─────────────────────────────────────┘  │
└───────────────────────────────────────────┘
```

Use the [impureim-sandwich](../impureim-sandwich/SKILL.md) skill for structuring logic inside each slice.

## Quality Check

Before finishing any feature implementation, verify:

1. Command slice has **5 files**: entry point, Domain, Handler, GatherInput, ProduceOutput
2. Query slice is **1 file** — simple read, no sandwich overhead
3. Slice does **not import from another slice** — only from `common/`
4. `common/` contains **only pure functions** (domain/) and infra wiring
5. Pure business logic is `fun` (not `suspend fun`), testable without mocks
6. Handler is **trivial 3-line orchestrator** — no logic inside
7. Error decisions mapped via `orderError()` in ProduceOutput — StatusPages handles HTTP
8. Route has **two overloads**: wiring (composition root) + HTTP protocol
