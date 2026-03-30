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
VSA:       [CreateOrder slice] ←→ [AssignShipping slice]  (independent)
```

## Slice = Use Case

Each slice handles **one request** — either a command (write) or a query (read).

| Slice type | Contains | Example |
|---|---|---|
| **Command** (write) | HTTP DTOs, route, Domain.kt, Handler, GatherInput, ProduceOutput | CreateOrder, CancelOrder, AssignShipping |
| **Query** (read) | Response DTO, route (often no pure logic needed) | GetOrder, GetProducts |

## Canonical Command Slice (with Impureim Sandwich inside)

### File structure — 5 files per command slice

```
assignShipping/
├── AssignShipping.kt              ← HTTP DTOs + route (wiring + protocol)
├── Domain.kt                      ← Input type + Decision sealed interface + pure logic
├── AssignShippingHandler.kt       ← Orchestrator (3-line composition)
├── GatherAssignShippingInput.kt   ← 🔴 READ phase
└── ProduceAssignShippingOutput.kt ← 🔴 WRITE phase + error mapping
```

### AssignShipping.kt — slice entry point

```kotlin
// ── HTTP DTOs ──
@Serializable
data class AssignShippingRequest(val address: String, val phone: String)

@Serializable
data class AssignShippingResponse(val orderId: String, val shippingFee: Int, val total: Int)

// ── Route (wiring) — composition root ──
fun Route.assignShippingRoute(db: Db) = assignShippingRoute(
    AssignShippingHandler(
        gatherInput = GatherAssignShippingInput(readOrder = { id -> db.orders[id] }),
        decide = ::decideShipping,
        produceOutput = ProduceAssignShippingOutput(storeOrder = { order -> db.orders[order.id] = order })
    )
)

// ── Route (HTTP protocol) ──
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

**Two route overloads:**
1. **Wiring** — connects real deps to handler phases
2. **HTTP protocol** — receives/responds, knows nothing about business logic

### Domain.kt — pure types + logic

```kotlin
data class AssignShippingInput(val order: Order?, val address: String, val phone: String)

sealed interface AssignShippingDecision {
    data class ShippingAssigned(val order: Order) : AssignShippingDecision
    data object NotFound : AssignShippingDecision
    data class WrongStatus(val current: OrderStatus) : AssignShippingDecision
}

fun decideShipping(input: AssignShippingInput): AssignShippingDecision { /* pure */ }
```

### Query slice (simple — no sandwich needed)

```kotlin
// getOrder/GetOrder.kt — single file
fun Route.getOrderRoute(db: Db) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        val order = db.orders[id] ?: domainError(ORDER_NOT_FOUND, "Order not found")
        call.respond(HttpStatusCode.OK, order.toResponse())
    }
}
```

**Key:** each slice picks its own approach. No forced uniformity.

## Project Structure

```
src/main/kotlin/com/example/
├── apps/
│   └── AppServer.kt                ← composition root (route registration)
├── features/
│   ├── catalog/
│   │   └── getProducts/
│   │       └── GetProducts.kt      ← query slice (single file)
│   └── orders/
│       ├── DomainError.kt          ← shared error vocabulary
│       ├── createOrder/
│       │   ├── CreateOrder.kt       ← HTTP DTOs + route
│       │   ├── Domain.kt            ← pure types + logic
│       │   ├── CreateOrderHandler.kt
│       │   ├── GatherCreateOrderInput.kt
│       │   └── ProduceCreateOrderOutput.kt
│       ├── assignShipping/
│       │   ├── AssignShipping.kt
│       │   ├── Domain.kt
│       │   ├── AssignShippingHandler.kt
│       │   ├── GatherAssignShippingInput.kt
│       │   └── ProduceAssignShippingOutput.kt
│       ├── payOrder/
│       │   └── ...                   ← same pattern
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
    │   └── Db.kt                    ← data store
    └── app/
        └── App.kt                   ← lifecycle
```

## Composition Root

Routes registered directly in the app entry point — no intermediate `Routing.kt`:

```kotlin
fun AppServer(db: Db = Db()) = App {
    val server = HttpServer(8080) {
        configureSerialization()
        configureErrorHandling()
        configureMonitoring()
        routing {
            getProductsRoute(db)
            createOrderRoute(db)
            getOrderRoute(db)
            assignShippingRoute(db)
            payOrderRoute(db)
            cancelOrderRoute(db)
        }
    }
    server.start()
    Teardown { server.stop(1000L, 1000L) }
}
```

## Error Handling: Shared Error Vocabulary

All order slices share a typed error enum mapping errors to HTTP statuses:

```kotlin
enum class DomainErrorCode(val status: HttpStatusCode) {
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),
    BLANK_ADDRESS(HttpStatusCode.BadRequest),
    // ...each slice adds its codes
}

fun domainError(code: DomainErrorCode, message: String): Nothing =
    throw DomainException(DomainError(code, message))
```

StatusPages catches `DomainException` and converts to HTTP response automatically.
ProduceOutput functions call `domainError()` for error decisions — they throw, never return.

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
    val result = AssignShipping(db)(request.orderId, reverseRequest)  // ← calling another slice!
}
```

### ✅ Extract shared pure logic instead

```kotlin
// common/domain/PricingRules.kt
fun calculateShippingFee(subtotal: Int): Int { /* pure */ }
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
fun calculateLineTotal(price: Int, quantity: Int): Int { /* pure */ }
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
7. Error decisions mapped via `domainError()` in ProduceOutput — StatusPages handles HTTP
8. Route has **two overloads**: wiring (composition root) + HTTP protocol
