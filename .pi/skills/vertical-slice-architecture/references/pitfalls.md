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

**Fix:** use 3-phase decomposition within each slice.

```
✅ orders/createOrder/
   ├── CreateOrder.kt             ← HTTP DTOs + route
   ├── Domain.kt                  ← pure types + logic
   ├── CreateOrderHandler.kt      ← trivial orchestrator
   ├── GatherCreateOrderInput.kt  ← READ phase
   └── ProduceCreateOrderOutput.kt ← WRITE phase
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
- `suspend` in `common/` (except `infra/` and `http/`) → 🚩
- Constructor-injected dependencies in `common/` → 🚩
- `common/` has more code than any 3 slices combined → 🚩

**Fix:** move impure logic into slices. Keep only pure functions in `common/domain/`.

### 3. Slice Calls Another Slice

**Symptom:** one slice imports from another slice's package.

```kotlin
// ❌ orders/cancelOrder/CancelOrder.kt
import com.example.features.orders.createOrder.createOrder  // importing from another slice!
```

**Fix:** extract shared logic into `common/domain/` as a pure function.

```kotlin
// ✅ common/domain/PricingRules.kt
fun calculateShippingFee(subtotal: Int): Int { /* pure */ }
// Both assignShipping and cancelOrder use it
```

### 4. Feature Factory — Inconsistent Domain Rules

**Symptom:** same business rule hardcoded differently in multiple slices.

```kotlin
// ❌ assignShipping/Domain.kt
val shippingFee = if (subtotal > 500) 0 else 50

// ❌ payOrder/Domain.kt
val shippingFee = if (subtotal > 400) 0 else 60  // different threshold!
```

**Fix:** extract shared domain rules into `common/domain/`.

```kotlin
// ✅ common/domain/PricingRules.kt
fun calculateShippingFee(subtotal: Int): Int =
    if (subtotal >= FREE_SHIPPING_THRESHOLD) 0 else SHIPPING_FEE
```

### 5. God Slice — Too Much in One File

**Symptom:** Domain.kt > 100 lines, business logic scattered across files.

**Fix:** keep Domain.kt focused on the pure decision. Complex calculations → `common/domain/`.

### 6. Business Logic in ProduceOutput

**Symptom:** ProduceOutput contains conditional logic beyond simple `when` + `domainError()`.

```kotlin
// ❌ ProduceOutput with business logic
fun ProduceOutput(store: (Order) -> Unit): suspend (Decision) -> Response = { decision ->
    when (decision) {
        is Created -> {
            if (decision.order.total > 1000) {  // ← business rule!
                applyVipDiscount(decision.order)  // ← side effect!
            }
            store(decision.order)
            Response(...)
        }
    }
}
```

**Fix:** all logic belongs in `decide()`. ProduceOutput only persists + maps errors.

### 7. Handler With Logic

**Symptom:** Handler does more than 3 lines (gather → decide → produce).

```kotlin
// ❌ Handler with extra logic
fun Handler(...) = { request ->
    val input = gatherInput(request)
    if (input.order == null) return@Handler errorResponse  // ← logic!
    val decision = decide(input)
    val enriched = enrichDecision(decision)  // ← extra step!
    produceOutput(enriched)
}
```

**Fix:** Handler must be a trivial 3-line composition. Move all logic to `decide()`.

---

## Code Review Checklist

Run through these checks when reviewing any PR:

### Slice Independence
- [ ] Slice does not import from another slice
- [ ] Slice only imports from `common/` and its own feature group's shared files (e.g., `DomainError.kt`)
- [ ] New feature = new slice folder, not modifications to existing slices

### 3-Phase Structure (for command slices)
- [ ] Domain.kt: pure logic is `fun` (not `suspend fun`)
- [ ] Domain.kt: pure logic has no IO calls inside
- [ ] Handler: trivial 3-line composition — gather → decide → produce
- [ ] GatherInput: collects all data, returns Input data class
- [ ] ProduceOutput: persists + maps errors via `domainError()`, NO business logic
- [ ] Decision modeled as sealed interface

### common/ Health
- [ ] No new `suspend` functions in `common/domain/`
- [ ] No new classes with constructor-injected dependencies
- [ ] No new files that import from a specific slice
- [ ] Shared code is genuinely used by 2+ slices (not pre-emptive extraction)

### Route Pattern
- [ ] Two route overloads: wiring (composition root) + HTTP protocol
- [ ] Wiring connects real deps (db, clock, uuid) to phase lambdas
- [ ] HTTP route knows nothing about business logic

### Consistency
- [ ] Files follow naming convention (Domain.kt, Handler, GatherInput, ProduceOutput)
- [ ] Error codes added to `DomainErrorCode` enum
- [ ] Tests exist for pure logic (no mocks needed)

### Size
- [ ] Domain.kt < 80 lines
- [ ] Each file < 50 lines (except Domain.kt)
- [ ] If bigger → should pure logic move to `common/domain/`?

---

## Warning Signs Over Time

| Timeframe | Healthy | Unhealthy |
|---|---|---|
| Month 1-3 | Slices small, some duplication OK | Premature extraction into common/ |
| Month 3-6 | Patterns emerge, extract to common/domain/ | common/ growing services |
| Month 6-12 | Stable common/, slices independent | Slices calling each other |
| Month 12+ | Feature groups → modules | common/ is 40%+ of codebase |
