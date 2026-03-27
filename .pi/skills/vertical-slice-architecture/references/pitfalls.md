# VSA Pitfalls & Review Checklist

How to spot when Vertical Slice Architecture is breaking down.

## 7 Failure Modes

### 1. Layered Architecture in Feature Folders

**Symptom:** feature folder has sub-folders named `domain/`, `application/`, `infrastructure/`, `presentation/`.

```
❌ features/orders/
   ├── domain/Order.kt
   ├── application/OrderService.kt        ← service layer is back
   ├── infrastructure/OrderRepoImpl.kt    ← DI + interface + impl
   └── presentation/OrderController.kt    ← controller layer
```

**Fix:** flatten into one file per slice.

```
✅ orders/placeOrder/
   └── PlaceOrder.kt    ← DTO + decision + pure fn + handler
```

### 2. common/ Becomes a Service Layer

**Symptom:** `common/` contains `suspend` functions, services, repositories, or orchestrators.

```
❌ common/
   ├── services/InventoryService.kt       ← class with DI
   ├── services/NotificationService.kt    ← class with DI
   └── helpers/OrderHelper.kt             ← suspend functions
```

**Litmus tests:**
- `suspend` in `common/` (except `infra/`) → 🚩
- Constructor-injected dependencies in `common/` → 🚩
- `common/` has more code than any 3 slices combined → 🚩

**Fix:** move impure logic into slices. Keep only pure functions in `common/`.

### 3. Slice Calls Another Slice

**Symptom:** one slice imports from another slice's package.

```kotlin
// ❌ orders/cancelOrder/CancelOrder.kt
import com.example.orders.placeOrder.buildOrder  // importing from another slice!

fun CancelOrder(...) = { request ->
    val refund = PlaceOrder(inventoryRepo, orderRepo, pricingRepo)(reverseRequest)  // calling another slice!
}
```

**Fix:** extract shared logic into `common/` as a pure function.

```kotlin
// ✅ common/domain/RefundRules.kt
fun calculateRefund(order: Order, cancelledAt: Instant): BigDecimal { /* pure */ }

// ✅ orders/cancelOrder/CancelOrder.kt
import com.example.common.domain.calculateRefund  // import from common, not another slice
```

### 4. Feature Factory — Inconsistent Domain Rules

**Symptom:** same business rule hardcoded differently in multiple slices.

```kotlin
// ❌ placeOrder/PlaceOrder.kt
if (total < BigDecimal("10.00")) return InvalidRequest("Min $10")

// ❌ updateOrder/UpdateOrder.kt
if (total < BigDecimal("15.00")) return InvalidRequest("Min $15")  // different threshold!
```

**Fix:** extract shared domain rules.

```kotlin
// ✅ common/domain/OrderRules.kt
object OrderRules {
    val MIN_ORDER_TOTAL = BigDecimal("10.00")
}

// Both slices:
if (!OrderRules.validateMinimumTotal(total)) return InvalidRequest("Below minimum")
```

### 5. God Slice — Too Much in One Handler

**Symptom:** slice file > 150 lines, handler > 80 lines, mixed pure and impure code.

```kotlin
// ❌ 200-line handler with interleaved reads and calculations
fun PlaceOrder(...) = { request ->
    val items = parseItems(request.items) ?: return@PlaceOrder HttpResult.BadRequest("...")
    val stock = inventoryRepo.checkStock(...)
    val missing = items.filter { ... }            // pure mixed with impure above
    if (missing.isNotEmpty()) return@PlaceOrder ...
    val prices = pricingRepo.getCurrentPrices(...)
    val subtotal = items.sumOf { ... }            // pure mixed with impure above
    val customer = customerRepo.findById(...)
    val discount = when { ... }                   // pure logic scattered
    val shipping = when { ... }                   // pure logic scattered
    // ... 50 more lines
}
```

**Fix:** extract pure logic into a separate function. Apply Recawr pattern.

```kotlin
// ✅ All reads upfront, all logic in one pure function
fun PlaceOrder(...) = { request ->
    val items = parseItems(request.items) ?: return@PlaceOrder HttpResult.BadRequest("...")
    val stock = inventoryRepo.checkStock(...)                     // 🔴 read
    val prices = pricingRepo.getCurrentPrices(...)                // 🔴 read
    val customer = customerRepo.findById(...)                     // 🔴 read
    val decision = buildOrder(items, stock, prices, customer)     // 🟢 all pure logic here
    when (decision) { ... }                                       // 🔴 write
}
```

### 6. Missing Conventions — Three Styles in One Project

**Symptom:** each developer writes slices differently — classes vs functions, different naming, different patterns.

```kotlin
// Dev A: class-based
class PlaceOrderHandler @Inject constructor(private val repo: OrderRepository) {
    suspend fun handle(request: PlaceOrderRequest): HttpResult { ... }
}

// Dev B: curried function
fun PlaceOrder(repo: OrderRepository): suspend (PlaceOrderRequest) -> HttpResult = { ... }

// Dev C: object with companion
object CancelOrder {
    suspend fun execute(request: CancelRequest, repo: OrderRepository): HttpResult { ... }
}
```

**Fix:** agree on slice anatomy as a team convention:

```
Team convention:
1. Handler = curried top-level function (PascalCase)
2. Pure logic = regular function (camelCase)
3. Decisions = sealed interface
4. One file per slice (unless > 120 lines)
5. Naming: [Verb][Noun] — PlaceOrder, GetCustomer, CancelSubscription
```

### 7. Semantic Diffusion — "VSA" That Isn't VSA

**Symptom:** team says "we do vertical slices" but still has shared services, controller classes, repository interfaces with implementations.

**Checklist — is it really VSA?**

| Check | VSA ✅ | Not VSA ❌ |
|---|---|---|
| Code grouped by | Feature / use case | Technical layer |
| Handler is | Top-level function | Method in controller class |
| Business logic | Pure function in same file | Service class in separate layer |
| DB access | Direct in handler (edge) | Through repository interface + impl |
| New feature means | New folder + file | Changes in 3+ existing files |
| Slices depend on | `common/` only | Each other / shared services |

---

## Code Review Checklist

Run through these checks when reviewing any PR:

### Slice Independence
- [ ] Slice does not import from another slice
- [ ] Slice only imports from `common/` and standard library
- [ ] New feature = new file(s), not modifications to existing slices

### Sandwich Structure (for command slices)
- [ ] Pure logic is `fun` (not `suspend fun`)
- [ ] Pure logic has no repository/service calls inside
- [ ] Handler follows Recawr: reads upfront → pure decide → writes at end
- [ ] Decision modeled as sealed class/interface (if branching)

### common/ Health
- [ ] No new `suspend` functions in `common/` (except `infra/`)
- [ ] No new classes with constructor-injected dependencies
- [ ] No new files that import from a specific slice
- [ ] Shared code is genuinely used by 2+ slices (not pre-emptive extraction)

### Consistency
- [ ] Handler follows team convention (curried function, naming)
- [ ] File structure matches project standard
- [ ] Tests exist for pure logic (no mocks needed)

### Size
- [ ] Slice file < 150 lines
- [ ] Handler function < 80 lines
- [ ] If bigger → should pure logic be extracted? Should slice be split?

---

## Warning Signs Over Time

| Timeframe | Healthy | Unhealthy |
|---|---|---|
| Month 1-3 | Slices small, some duplication OK | Premature extraction into common/ |
| Month 3-6 | Patterns emerge, extract to common/ | common/ growing services |
| Month 6-12 | Stable common/, slices independent | Slices calling each other |
| Month 12+ | Feature groups → modules | common/ is 40%+ of codebase |

## Team Readiness Checklist

VSA requires a team that can:

- [ ] Recognize code smells (Long Method, Feature Envy, Duplicate Code)
- [ ] Practice refactoring (Extract Method, Extract Function, Move to common/)
- [ ] Distinguish pure from impure code
- [ ] Resist premature abstraction ("I might need this later")
- [ ] Tolerate short-term duplication for long-term independence
- [ ] Do code reviews that check architectural boundaries
