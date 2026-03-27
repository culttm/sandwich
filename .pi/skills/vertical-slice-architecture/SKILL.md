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
VSA:       [PlaceOrder slice] ←→ [GetOrder slice]  (independent)
```

## Slice = Use Case = One File

Each slice handles **one request** — either a command (write) or a query (read).

| Slice type | Contains | Example |
|---|---|---|
| **Command** (write) | Request DTO, Decision sealed class, pure logic fn, handler | PlaceOrder, CancelOrder |
| **Query** (read) | Response DTO, handler (often no pure logic needed) | GetOrder, ListOrders |

## Canonical Slice Structure

### Command slice (with Impureim Sandwich inside)

```kotlin
// orders/placeOrder/PlaceOrder.kt — one file, one slice

// --- DTOs ---
data class PlaceOrderRequest(val customerId: String, val items: List<OrderItemDto>)

// --- Decision ---
sealed interface OrderDecision {
    data class Fulfillable(val order: Order, val reservations: List<Reservation>) : OrderDecision
    data class OutOfStock(val missing: List<ProductId>) : OrderDecision
}

// --- Pure logic (NOT suspend) ---
fun buildOrder(
    items: List<OrderItem>,
    stock: Map<ProductId, Int>,
    prices: Map<ProductId, BigDecimal>
): OrderDecision {
    val missing = items.filter { (stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return OutOfStock(missing.map { it.productId })
    val total = items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
    return Fulfillable(
        order = Order(items = items, total = total),
        reservations = items.map { Reservation(it.productId, it.quantity) }
    )
}

// --- Handler (Recawr Sandwich) ---
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository,
    pricingRepo: PricingRepository
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 parse
    val items = parseOrderItems(request.items)
        ?: return@PlaceOrder HttpResult.BadRequest("Invalid items")
    // 🔴 read
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val prices = pricingRepo.getCurrentPrices(items.map { it.productId })
    // 🟢 decide
    val decision = buildOrder(items, stock, prices)
    // 🔴 write
    when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
    }
}
```

### Query slice (simple — no sandwich needed)

```kotlin
// orders/getOrder/GetOrder.kt — one file, one slice

data class OrderResponse(val id: String, val status: String, val total: BigDecimal)

fun GetOrder(db: Database): suspend (OrderId) -> HttpResult = { orderId ->
    val row = db.query("SELECT id, status, total FROM orders WHERE id = ?", orderId)
    if (row != null) HttpResult.Ok(OrderResponse(row.id, row.status, row.total))
    else HttpResult.NotFound("Order $orderId not found")
}
```

**Key:** each slice picks its own approach. No forced uniformity.

## Project Structure

```
src/main/kotlin/com/example/
├── orders/
│   ├── placeOrder/
│   │   └── PlaceOrder.kt          ← command slice (sandwich)
│   ├── getOrder/
│   │   └── GetOrder.kt            ← query slice (simple)
│   ├── cancelOrder/
│   │   └── CancelOrder.kt         ← command slice
│   └── listOrders/
│       └── ListOrders.kt          ← query slice
├── customers/
│   ├── createCustomer/
│   │   └── CreateCustomer.kt
│   └── getCustomer/
│       └── GetCustomer.kt
└── common/                         ← minimal shared code
    ├── validation/
    │   └── OrderValidation.kt      ← pure functions only
    ├── domain/
    │   └── OrderRules.kt           ← pure business rules
    └── infra/
        ├── Database.kt
        └── HttpResult.kt
```

## Decision Tree

### Creating a new feature
→ Create new slice folder with a single file
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

This is **not** VSA. It's Clean Architecture with renamed folders.

### ✅ True vertical slice

```
orders/placeOrder/
└── PlaceOrder.kt    ← everything in one file: DTO, decision, logic, handler
```

### ❌ Slice calls another slice

```kotlin
fun CancelOrder(...): suspend (CancelRequest) -> HttpResult = { request ->
    val refund = CalculateRefund(orderRepo)(request.orderId)  // ← calling another slice!
    // ...
}
```

### ✅ Extract shared pure logic instead

```kotlin
// common/domain/RefundRules.kt
fun calculateRefund(order: Order, cancelledAt: Instant): BigDecimal { /* pure */ }

// Both slices use the shared pure function:
// cancelOrder/CancelOrder.kt:   val refund = calculateRefund(order, now)
// processReturn/ProcessReturn.kt: val refund = calculateRefund(order, now)
```

### ❌ common/ becomes a service layer

```kotlin
// common/services/InventoryService.kt ← 🚩
class InventoryService(private val repo: InventoryRepository) {
    suspend fun checkAndReserve(...) { ... }
}
```

### ✅ common/ contains only pure functions

```kotlin
// common/validation/OrderValidation.kt ← ✅
fun parseOrderItems(dtos: List<OrderItemDto>): List<OrderItem>? { /* pure */ }
```

## Slice Complexity Ladder

Each slice chooses the **simplest approach that works**:

```
Complexity low → high:

1. Direct query      — simple DB read, return DTO
2. Transaction script — sequential steps, no branching
3. Impureim sandwich  — read → pure decide → write
4. Multi-step saga    — if single sandwich doesn't fit, split into multiple
```

**Rule:** start at level 1. Move up only when complexity demands it.

## VSA + Impureim Sandwich

VSA answers: **where** does the code live? (by feature)
Impureim Sandwich answers: **how** is the code structured inside? (pure core + impure shell)

```
┌───────────────────────────────────┐
│  VSA — organization by feature   │
│  ┌─────────────────────────────┐ │
│  │  Impureim Sandwich — logic  │ │
│  │  impure → pure → impure     │ │
│  └─────────────────────────────┘ │
└───────────────────────────────────┘
```

Use the [impureim-sandwich](../impureim-sandwich/SKILL.md) skill for structuring logic inside each slice.

## Quality Check

Before finishing any feature implementation, verify:

1. Slice is **one file** (or one folder with max 2-3 files for complex cases)
2. Slice does **not import from another slice** — only from `common/`
3. `common/` contains **only pure functions** — no `suspend`, no repositories
4. Command slices have **clear sandwich structure** (read → decide → write)
5. Pure business logic is `fun` (not `suspend fun`), testable without mocks
6. Handler is a **curried function**, not a class with injected dependencies
