# Refactoring to Impureim Sandwich

Step-by-step: from OOP class with DI to 3-phase sandwich.

## The 5-Step Evolution

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

**Problem:** `placeOrder` mixes business logic with IO — untestable without mocks.

### Step 2: Extract pure decision function

Identify the business logic that doesn't need IO. Create an Input data class and sealed Decision:

```kotlin
data class CreateOrderInput(
    val items: List<OrderItemRequest>,
    val stock: Map<ProductId, Int>,
    val prices: Map<ProductId, BigDecimal>
)

sealed interface CreateOrderDecision {
    data class Fulfillable(val order: Order) : CreateOrderDecision
    data class OutOfStock(val missing: List<ProductId>) : CreateOrderDecision
}

// 🟢 Pure: takes data, returns decision — NO suspend, NO repo
fun buildOrder(input: CreateOrderInput): CreateOrderDecision {
    val missing = input.items.filter { (input.stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return OutOfStock(missing.map { it.productId })
    return Fulfillable(Order(input.items, calculateTotal(input)))
}
```

### Step 3: Extract GatherInput (READ phase)

Move all IO reads into a factory function that returns the Input:

```kotlin
fun GatherCreateOrderInput(
    readStock: (List<ProductId>) -> Map<ProductId, Int>,
    readPrices: (List<ProductId>) -> Map<ProductId, BigDecimal>
): (CreateOrderRequest) -> CreateOrderInput = { request ->
    val productIds = request.items.map { it.productId }
    CreateOrderInput(
        items = request.items,
        stock = readStock(productIds),
        prices = readPrices(productIds)
    )
}
```

### Step 4: Extract ProduceOutput (WRITE phase)

Move persistence + error mapping into a separate function:

```kotlin
fun ProduceCreateOrderOutput(
    storeOrder: (Order) -> Unit
): suspend (CreateOrderDecision) -> CreateOrderResponse = { decision ->
    when (decision) {
        is Fulfillable -> {
            storeOrder(decision.order)
            CreateOrderResponse(orderId = decision.order.id)
        }
        is OutOfStock ->
            orderError(OUT_OF_STOCK, "Missing: ${decision.missing}")
    }
}
```

### Step 5: Compose the Handler + wire in Route

```kotlin
// Handler — trivial 3-line composition
fun CreateOrderHandler(
    gatherInput: (CreateOrderRequest) -> CreateOrderInput,
    decide: (CreateOrderInput) -> CreateOrderDecision,
    produceOutput: suspend (CreateOrderDecision) -> CreateOrderResponse
): suspend (CreateOrderRequest) -> CreateOrderResponse = { request ->
    val input = gatherInput(request)
    val decision = decide(input)
    produceOutput(decision)
}

// Route — composition root, wires real deps
fun Route.createOrderRoute(db: Db) = createOrderRoute(
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readStock = { ids -> db.stock.filterKeys { it in ids } },
            readPrices = { ids -> db.prices.filterKeys { it in ids } }
        ),
        decide = ::buildOrder,
        produceOutput = ProduceCreateOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
)
```

## Refactoring Checklist

For each method in a service class:

1. **List all IO calls** — find every `suspend`, repo call, HTTP call, time/random
2. **Create Input data class** — everything the logic needs, as plain data
3. **Create Decision sealed interface** — each branch = sealed subtype
4. **Extract pure function** — `fun decide(input): Decision` — no IO, no suspend
5. **Extract GatherInput** — factory that collects data into Input, deps as lambdas
6. **Extract ProduceOutput** — factory that persists + maps errors, deps as lambdas
7. **Create Handler** — trivial 3-line composition of the phases
8. **Wire in Route** — connect real deps (db, clock, uuid) to lambda params
9. **Delete the service class** — it's been replaced by the phase functions

## Sealed Class Extraction

### When to use sealed class for decisions

Use when the pure function has **branching outcomes** that require **different actions**:

```kotlin
sealed interface SetDeliveryDecision {
    data class DeliverySet(val order: Order) : SetDeliveryDecision
    data object NotFound : SetDeliveryDecision
    data class WrongStatus(val current: OrderStatus) : SetDeliveryDecision
    data class BlankAddress(val message: String) : SetDeliveryDecision
}
```

### Three tools for modeling decisions

| Tool | When |
|------|------|
| `sealed class/interface` | Multiple distinct outcomes with different data |
| `Result<T>` | Success/failure binary |
| `T?` (nullable) | Value present or absent |

For command slices, sealed interface is almost always the right choice —
it makes all branches explicit in the `when` of ProduceOutput.

## Lambda Injection vs Constructor DI

### ❌ Old: constructor injection (class-based)
```kotlin
class OrderService(
    private val orderRepo: OrderRepository,    // interface + impl
    private val inventoryRepo: InventoryRepository
) { ... }
```

### ✅ New: lambda injection (function-based)
```kotlin
fun GatherCreateOrderInput(
    readMenu: () -> Map<String, MenuItem>,     // plain lambda
    readExtras: () -> Map<String, ExtraItem>,
    generateId: () -> String,
    now: () -> Instant
): (CreateOrderRequest) -> CreateOrderInput = { ... }
```

**Key difference:** lambdas are wired at the route level with real implementations.
No interfaces, no implementations, no DI container.

## Common Refactoring Traps

| Trap | Fix |
|------|-----|
| "I need DB result to decide what to read next" | Read both upfront in GatherInput, let decide() choose which to use |
| "I need to write intermediate results" | Collect all decisions first, write in batch in ProduceOutput |
| "My pure function needs the current time" | Pass `now` as field in Input, generate in GatherInput |
| "I log inside business logic for debugging" | Return structured Decision, log in ProduceOutput or route |
| "I need to call an external API mid-logic" | Read API result in GatherInput, pass as field in Input |
| "ProduceOutput has business logic" | Move it to decide(). ProduceOutput should only persist + map errors |
