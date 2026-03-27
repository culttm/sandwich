# Refactoring to Impureim Sandwich

Step-by-step: from OOP class with DI to functional sandwich.

## The 4-Step Evolution

### Step 1: Classic OOP with DI (starting point)

```kotlin
class OrderService(
    private val inventoryRepo: InventoryRepository,
    private val orderRepo: OrderRepository
) {
    suspend fun placeOrder(request: PlaceOrderRequest): OrderId? {
        val stock = inventoryRepo.checkStock(request.productIds())  // impure inside logic
        val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }
        if (outOfStock.isNotEmpty()) return null
        val order = Order(items = request.items, total = calculateTotal(request))
        return orderRepo.create(order)  // impure inside logic
    }
}
```

**Problem:** `placeOrder` looks like business logic but is impure — depends on repositories.

### Step 2: Extract pure decision function

Identify the business logic that doesn't need IO:

```kotlin
// 🟢 Pure: takes data, returns decision
fun buildOrder(
    stock: Map<ProductId, Int>,
    request: PlaceOrderRequest
): OrderDecision {
    val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }
    return if (outOfStock.isNotEmpty())
        OutOfStock(outOfStock.map { it.productId })
    else
        Fulfillable(Order(request.items, calculateTotal(request)))
}
```

### Step 3: Model the decision as sealed class

```kotlin
sealed interface OrderDecision {
    data class Fulfillable(val order: Order) : OrderDecision
    data class OutOfStock(val missing: List<ProductId>) : OrderDecision
}
```

### Step 4: Compose the sandwich on the edge

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    // 🔴 read
    val stock = inventoryRepo.checkStock(request.productIds())

    // 🟢 decide (pure — no repo, no suspend)
    val decision = buildOrder(stock, request)

    // 🔴 write
    return when (decision) {
        is Fulfillable -> HttpResult.Created(orderRepo.create(decision.order))
        is OutOfStock -> HttpResult.Conflict(decision.missing)
    }
}
```

## Refactoring Checklist

For each method in a service class:

1. **List all IO calls** — find every `suspend`, repo call, HTTP call, time/random
2. **Group reads vs writes** — which IO gathers data, which IO produces effects
3. **Extract the logic between** — everything that transforms data without IO → pure function
4. **Model output as sealed class** — if the logic has branches, each branch = sealed subtype
5. **Move IO to the edges** — reads before pure, writes after pure
6. **Remove the class** — if the service has no state, replace with a top-level function or curried factory

## Sealed Class Extraction

### When to use sealed class for decisions

Use when the pure function has **branching outcomes** that require **different write actions**:

```kotlin
sealed class DiscountDecision {
    data class ApplyDiscount(val rate: BigDecimal, val reason: String) : DiscountDecision()
    data object NoDiscount : DiscountDecision()
}

// 🟢 Pure
fun decideDiscount(order: Order, history: CustomerHistory): DiscountDecision {
    if (history.totalOrders >= 50) return ApplyDiscount("0.15".toBigDecimal(), "loyalty_gold")
    if (history.totalOrders >= 10) return ApplyDiscount("0.05".toBigDecimal(), "loyalty_silver")
    if (order.total > "500".toBigDecimal()) return ApplyDiscount("0.10".toBigDecimal(), "large_order")
    return NoDiscount
}
```

### When sealed class is overkill

If the pure function returns a single value that is either used or not — use nullable or Result:

```kotlin
// Simple: nullable is enough
fun parseDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw) }.getOrNull()

// Simple: Result is enough
fun validateOrder(dto: PlaceOrderDto): Result<ValidatedOrder> = ...
```

### Three tools for modeling decisions

| Tool | When |
|------|------|
| `sealed class/interface` | Multiple distinct outcomes with different data |
| `Result<T>` | Success/failure binary |
| `T?` (nullable) | Value present or absent |

## Currying: Right vs Wrong

### ❌ Wrong: curry hides impurity

```kotlin
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository
): suspend (PlaceOrderRequest) -> OrderId? = { request ->
    val stock = inventoryRepo.checkStock(request.productIds())  // impure inside!
    // logic mixed with IO
    orderRepo.create(order)  // impure inside!
}
```

Currying ≡ Constructor Injection. The function is still impure.

### ✅ Right: curry as composition root with sandwich inside

```kotlin
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    val stock = inventoryRepo.checkStock(request.productIds())  // 🔴 read
    val decision = buildOrder(stock, request)                    // 🟢 pure (NO repo!)
    when (decision) {                                            // 🔴 write
        is Fulfillable -> HttpResult.Created(orderRepo.create(decision.order))
        is OutOfStock -> HttpResult.Conflict(decision.missing)
    }
}
```

The key difference: `buildOrder` does NOT call any repository. It receives data as arguments.

## Common Refactoring Traps

| Trap | Fix |
|------|-----|
| "I need DB result to decide what to read next" | Read both upfront, let pure function decide which to use |
| "I need to write intermediate results" | Collect all decisions first, write in batch at the end |
| "My pure function needs the current time" | Pass `now` as argument from edge |
| "I log inside business logic for debugging" | Return structured data, log on edge |
| "I need to call an external API mid-logic" | Read API result upfront, pass as argument |
