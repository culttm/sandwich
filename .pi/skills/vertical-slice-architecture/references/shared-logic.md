# Shared Logic in VSA

The hardest question in Vertical Slice Architecture: where does shared code live?

## The Rule

> Shared code lives in `common/`. It is **pure**, **stateless**, and **has no dependencies on infrastructure**.

## What CAN Go in common/

| Category | Example | Why OK |
|---|---|---|
| Pure calculations | `calculateLineTotal(price, extras): Int` | No IO, deterministic |
| Pure pricing rules | `calculateDeliveryFee(subtotal): Int` | Business constant/logic |
| Domain types | `Order`, `OrderStatus`, `MenuItem` | Data types |
| Business constants | `MAX_ITEMS_PER_ORDER`, `FREE_DELIVERY_THRESHOLD` | Static data |
| Error vocabulary | `OrderErrorCode`, `OrderException`, `orderError()` | Shared within feature group |
| Infrastructure | `Db`, `HttpServer`, `ErrorHandling` | Wiring code |

## What CANNOT Go in common/

| Category | Example | Why NOT |
|---|---|---|
| Suspend functions | `suspend fun fetchAndValidate(...)` | Impure — contains IO |
| Repository interfaces | `interface OrderRepository` | Forces DI pattern |
| Services | `class InventoryService(repo)` | Becomes a hidden layer |
| Functions taking repos | `fun check(repo: InventoryRepo)` | Impure by dependency |
| Orchestration / workflow | `fun processOrderFlow(...)` | Business logic + IO mixed |

## Litmus Tests

### 🚩 Test 1: Does it have `suspend`?

```kotlin
// ❌ common/domain/OrderValidation.kt
suspend fun validateAndEnrich(dto: OrderDto, repo: ProductRepo): ValidatedOrder? {
    val products = repo.findByIds(dto.productIds)  // suspend = impure!
}
```

Fix: move the IO to GatherInput, pass data to the pure function.

```kotlin
// ✅ common/domain/OrderValidation.kt
fun validateOrder(dto: OrderDto, products: Map<ProductId, Product>): ValidatedOrder? {
    // pure — products already fetched by GatherInput
}
```

### 🚩 Test 2: Does it accept a repository/service as parameter?

```kotlin
// ❌ common/domain/PricingCalculator.kt
fun calculateTotal(items: List<OrderItem>, pricingRepo: PricingRepository): Int {
    val prices = pricingRepo.getCurrentPrices(...)  // impure dependency!
}
```

Fix: accept data, not the source of data.

```kotlin
// ✅ common/domain/PricingRules.kt
fun calculateLineTotal(sandwichPrice: Int, extraPrices: List<Int>): Int {
    return sandwichPrice + extraPrices.sum()  // pure
}
```

### 🚩 Test 3: Is common/ growing faster than slices?

```
Healthy:    common/ = 15% of code, slices = 85%
Unhealthy:  common/ = 50% of code, slices = 50%  ← hidden service layer
```

### 🚩 Test 4: Is common/ importing from a specific slice?

```kotlin
// ❌ common/helpers/OrderHelper.kt
import com.sandwich.features.orders.createOrder.CreateOrderRequest  // importing from a slice!
```

`common/` must NEVER import from a slice. Dependencies flow one way: slice → common.

## The Duplication Rule

> Duplication between slices is **acceptable** until the third occurrence.

### Occurrence 1-2: leave it

```kotlin
// orders/createOrder/Domain.kt
val total = subtotal - discount

// orders/setDelivery/Domain.kt
val total = subtotal - discount + deliveryFee  // similar but different — leave it
```

### Occurrence 3: extract to common/

```kotlin
// common/domain/PricingRules.kt
fun calculateDeliveryFee(subtotal: Int): Int = if (subtotal >= FREE_DELIVERY_THRESHOLD) 0 else DELIVERY_FEE
```

### Why not extract immediately?

- **Premature abstraction** creates wrong boundaries
- First two slices may look similar but evolve differently
- Third occurrence confirms the pattern is real

## Shared Error Vocabulary

Error codes shared within a feature group — one enum per aggregate:

```kotlin
// orders/OrderError.kt
enum class OrderErrorCode(val status: HttpStatusCode) {
    // createOrder
    BLANK_NAME(HttpStatusCode.BadRequest),
    EMPTY_ORDER(HttpStatusCode.BadRequest),
    UNKNOWN_SANDWICH(HttpStatusCode.BadRequest),

    // shared across slices
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),

    // setDelivery
    BLANK_ADDRESS(HttpStatusCode.BadRequest),

    // cancelOrder
    TOO_LATE(HttpStatusCode.Conflict),
}
```

Each slice adds its own codes. `ProduceOutput` calls `orderError(CODE, message)`.
StatusPages catches `OrderException` → HTTP response. No manual HTTP status mapping in slices.

## common/ Structure

```
common/
├── domain/
│   ├── PricingRules.kt        ← pure business rules (calculateLineTotal, calculateDiscount, etc.)
│   └── Types.kt               ← shared domain types (Order, OrderLine, OrderStatus, DeliveryInfo)
├── http/
│   ├── ErrorHandling.kt       ← StatusPages config (catches OrderException)
│   ├── HttpServer.kt          ← Ktor server factory
│   ├── Monitoring.kt          ← call logging
│   └── Serialization.kt       ← JSON config
├── infra/
│   └── Db.kt                  ← in-memory store (sandwiches, extras, orders, stock)
└── app/
    └── App.kt                 ← lifecycle (start/teardown)
```

**Note:** `common/http/` and `common/infra/` contain impure infrastructure code — that's OK,
it's wiring, not business logic. `common/domain/` must remain **purely pure**.
