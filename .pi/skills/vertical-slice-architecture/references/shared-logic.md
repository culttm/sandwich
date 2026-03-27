# Shared Logic in VSA

The hardest question in Vertical Slice Architecture: where does shared code live?

## The Rule

> Shared code lives in `common/`. It is **pure**, **stateless**, and **has no dependencies on infrastructure**.

## What CAN Go in common/

| Category | Example | Why OK |
|---|---|---|
| Pure validation | `parseOrderItems(dtos): List<OrderItem>?` | No IO, no state |
| Pure calculations | `loyaltyDiscount(tier, subtotal): BigDecimal` | Deterministic |
| Domain rules | `OrderRules.MIN_TOTAL`, `isEligibleForRefund(order)` | Business constants/logic |
| Value objects | `ProductId`, `OrderId`, `Money` | Data types |
| DTOs shared between slices | `HttpResult`, `PageRequest`, `SortOrder` | Transport types |
| Extension functions | `String.toProductId()`, `BigDecimal.roundCents()` | Pure transforms |
| Enums / constants | `OrderStatus`, `ShippingZone` | Static data |

## What CANNOT Go in common/

| Category | Example | Why NOT |
|---|---|---|
| Suspend functions | `suspend fun fetchAndValidate(...)` | Impure — contains IO |
| Repository interfaces | `interface OrderRepository` | Forces DI pattern |
| Services | `class InventoryService(repo)` | Becomes a hidden layer |
| Functions taking repos | `fun check(repo: InventoryRepo)` | Impure by dependency |
| Orchestration / workflow | `fun processOrderFlow(...)` | Business logic + IO mixed |
| Anything with side effects | `fun notify(email: String)` | Impure — sends email |

## Litmus Tests

### 🚩 Test 1: Does it have `suspend`?

```kotlin
// ❌ common/validation/OrderValidation.kt
suspend fun validateAndEnrich(dto: OrderDto, repo: ProductRepo): ValidatedOrder? {
    val products = repo.findByIds(dto.productIds)  // suspend = impure!
    // ...
}
```

If `suspend` is in `common/`, it's a 🚩. Move the IO to the slice, pass data to the pure function.

```kotlin
// ✅ common/validation/OrderValidation.kt
fun validateOrder(dto: OrderDto, products: Map<ProductId, Product>): ValidatedOrder? {
    // pure — products already fetched by the slice
}
```

### 🚩 Test 2: Does it accept a repository/service as parameter?

```kotlin
// ❌ common/pricing/PricingCalculator.kt
fun calculateTotal(items: List<OrderItem>, pricingRepo: PricingRepository): BigDecimal {
    val prices = pricingRepo.getCurrentPrices(...)  // impure dependency!
    // ...
}
```

Fix: accept data, not the source of data.

```kotlin
// ✅ common/pricing/PricingCalculator.kt
fun calculateTotal(items: List<OrderItem>, prices: Map<ProductId, BigDecimal>): BigDecimal {
    return items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
}
```

### 🚩 Test 3: Is common/ growing faster than slices?

Track the ratio. If `common/` has more lines than the average slice — something is wrong.

```
Healthy:    common/ = 15% of code, slices = 85%
Unhealthy:  common/ = 50% of code, slices = 50%  ← hidden service layer
```

### 🚩 Test 4: Is common/ importing from a specific slice?

```kotlin
// ❌ common/helpers/OrderHelper.kt
import com.example.orders.placeOrder.PlaceOrderRequest  // importing from a slice!
```

`common/` must NEVER import from a slice. Dependencies flow one way: slice → common.

## The Duplication Rule

> Duplication between slices is **acceptable** until the third occurrence.

### Occurrence 1-2: leave it

```kotlin
// orders/placeOrder/PlaceOrder.kt
fun parseOrderItems(dtos: List<OrderItemDto>): List<OrderItem>? { ... }

// orders/updateOrder/UpdateOrder.kt
fun parseOrderItems(dtos: List<OrderItemDto>): List<OrderItem>? { ... }  // same code — OK for now
```

### Occurrence 3: extract to common/

```kotlin
// common/validation/OrderValidation.kt
fun parseOrderItems(dtos: List<OrderItemDto>): List<OrderItem>? { ... }  // ← extracted

// All three slices now import from common/
```

### Why not extract immediately?

- **Premature abstraction** creates wrong boundaries
- First two slices may look similar but evolve differently
- Third occurrence confirms the pattern is real

## Shared Domain Rules

Business rules that apply across multiple slices — extract as **pure objects**:

```kotlin
// common/domain/OrderRules.kt
object OrderRules {
    val MIN_ORDER_TOTAL = BigDecimal("10.00")
    val MAX_ITEMS_PER_ORDER = 50
    val REFUND_WINDOW_DAYS = 30L

    fun isEligibleForRefund(order: Order, now: Instant): Boolean =
        Duration.between(order.createdAt, now).toDays() <= REFUND_WINDOW_DAYS
            && order.status != OrderStatus.SHIPPED

    fun validateMinimumTotal(total: BigDecimal): Boolean =
        total >= MIN_ORDER_TOTAL
}
```

Slices use these rules but **don't depend on each other**:

```kotlin
// orders/placeOrder/PlaceOrder.kt
if (!OrderRules.validateMinimumTotal(total)) return InvalidRequest("Below minimum")

// orders/cancelOrder/CancelOrder.kt
if (!OrderRules.isEligibleForRefund(order, now)) return NotRefundable("Window expired")
```

## common/ Structure

```
common/
├── domain/
│   ├── OrderRules.kt          ← pure business rules
│   ├── PricingRules.kt        ← pure pricing logic
│   └── types/
│       ├── ProductId.kt       ← value objects
│       ├── OrderId.kt
│       └── Money.kt
├── validation/
│   └── OrderValidation.kt    ← pure parsing/validation
├── infra/
│   ├── Database.kt            ← DB connection (impure, but infra)
│   ├── HttpResult.kt          ← response type
│   └── RouteConfig.kt         ← routing setup
└── extensions/
    └── BigDecimalExt.kt       ← pure extension functions
```

**Note:** `common/infra/` is the only place where impure code is OK — it's infrastructure wiring, not business logic.

## Anti-Pattern: common/ Evolution Into Service Layer

```
Month 1:    common/DateUtils.kt                          ← fine
Month 3:    common/OrderValidation.kt                    ← fine
Month 6:    common/InventoryService.kt                   ← 🚩 service!
Month 9:    common/services/OrderOrchestrator.kt         ← 🚩🚩 orchestrator!
Month 12:   common/services/ has 15 files                ← you rebuilt layered architecture
```

**Prevention:**
- Code review rule: any new file in `common/` must justify why it's not in a slice
- `common/` has no `suspend` functions (except `infra/`)
- `common/` has no classes with constructor-injected dependencies
