# Slice Anatomy

How to structure each individual slice based on its complexity.

## Level 1: Direct Query (simplest)

No business logic. Read data, return DTO.

```kotlin
// orders/getOrder/GetOrder.kt

data class OrderResponse(val id: String, val status: String, val total: BigDecimal)

fun GetOrder(db: Database): suspend (OrderId) -> HttpResult = { orderId ->
    val row = db.query("SELECT id, status, total FROM orders WHERE id = ?", orderId)
    if (row != null) HttpResult.Ok(OrderResponse(row.id, row.status, row.total))
    else HttpResult.NotFound("Order $orderId not found")
}
```

**When:** GET endpoints, list views, simple lookups.
**Contains:** Response DTO + handler. That's it.
**Tests:** integration test against real DB (or test container).

---

## Level 2: Transaction Script

Sequential steps without complex branching. Logic is straightforward enough
that sealed class decisions would be overkill.

```kotlin
// customers/createCustomer/CreateCustomer.kt

data class CreateCustomerRequest(val email: String, val name: String)

fun CreateCustomer(
    customerRepo: CustomerRepository
): suspend (CreateCustomerRequest) -> HttpResult = { request ->
    // 🟢 validate
    if (!isValidEmail(request.email)) return@CreateCustomer HttpResult.BadRequest("Invalid email")
    if (request.name.isBlank()) return@CreateCustomer HttpResult.BadRequest("Name required")

    // 🔴 check
    val existing = customerRepo.findByEmail(request.email)
    if (existing != null) return@CreateCustomer HttpResult.Conflict("Email already registered")

    // 🔴 write
    val id = customerRepo.create(Customer(email = request.email, name = request.name.trim()))
    HttpResult.Created(id)
}

// 🟢 Pure helper
fun isValidEmail(email: String): Boolean =
    email.contains("@") && email.contains(".") && email.length >= 5
```

**When:** simple CRUD, few validation rules, no branching outcomes.
**Contains:** Request DTO + handler + maybe small pure helpers.
**Tests:** pure helpers tested with unit tests; handler tested with integration test.

---

## Level 3: Impureim Sandwich (default for commands)

Business logic with branching outcomes. Full sandwich: read → decide → write.

```kotlin
// orders/placeOrder/PlaceOrder.kt

// --- Request ---
data class PlaceOrderRequest(
    val customerId: CustomerId,
    val items: List<OrderItemDto>,
    val requestedDelivery: String
)

// --- Decision (sealed class) ---
sealed interface OrderDecision {
    data class Fulfillable(val order: Order, val reservations: List<Reservation>) : OrderDecision
    data class OutOfStock(val missing: List<ProductId>) : OrderDecision
    data class PriceChanged(val newPrices: Map<ProductId, BigDecimal>) : OrderDecision
}

// --- Pure logic (NOT suspend) ---
fun buildOrder(
    items: List<OrderItem>,
    stock: Map<ProductId, Int>,
    prices: Map<ProductId, BigDecimal>,
    loyaltyTier: LoyaltyTier,
    shippingZone: ShippingZone
): OrderDecision {
    val missing = items.filter { (stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return OutOfStock(missing.map { it.productId })

    val priceChanges = items.filter { prices[it.productId] != it.expectedPrice }
    if (priceChanges.isNotEmpty()) return PriceChanged(
        priceChanges.associate { it.productId to prices[it.productId]!! }
    )

    val subtotal = items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
    val discount = loyaltyDiscount(loyaltyTier, subtotal)
    val shipping = shippingCost(shippingZone)
    val total = subtotal - discount + shipping

    return Fulfillable(
        order = Order(items, subtotal, discount, shipping, total),
        reservations = items.map { Reservation(it.productId, it.quantity) }
    )
}

// --- Handler (Recawr Sandwich) ---
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository,
    pricingRepo: PricingRepository,
    customerRepo: CustomerRepository
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 parse
    val items = parseOrderItems(request.items)
        ?: return@PlaceOrder HttpResult.BadRequest("Invalid items")
    val deliveryDate = parseDate(request.requestedDelivery)
        ?: return@PlaceOrder HttpResult.BadRequest("Invalid date")

    // 🔴 read
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val prices = pricingRepo.getCurrentPrices(items.map { it.productId })
    val customer = customerRepo.findById(request.customerId)

    // 🟢 decide
    val decision = buildOrder(items, stock, prices, customer.loyaltyTier, customer.shippingZone)

    // 🔴 write
    when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
        is PriceChanged -> HttpResult.Conflict("Prices changed: ${decision.newPrices}")
    }
}
```

**When:** commands with business rules, multiple outcomes, non-trivial logic.
**Contains:** Request DTO + Decision sealed class + pure function(s) + handler.
**Tests:** pure function tested with unit tests (no mocks, no runTest); handler tested with integration test.

---

## Level 4: Multi-step (split into sub-sandwiches)

When a single sandwich can't handle the flow — e.g., you need to read,
decide, write, then read again based on the write result.

**Solution:** split into two sandwiches orchestrated by a higher-level handler.

```kotlin
// payments/processPayment/ProcessPayment.kt

// --- Sub-sandwich 1: validate & authorize ---
sealed interface AuthDecision {
    data class Authorized(val authCode: String, val amount: BigDecimal) : AuthDecision
    data class Declined(val reason: String) : AuthDecision
}

fun decideAuth(order: Order, paymentMethod: PaymentMethod, balance: BigDecimal): AuthDecision {
    if (order.total > balance) return Declined("Insufficient balance")
    return Authorized(authCode = "AUTH-${order.id}", amount = order.total)
}

// --- Sub-sandwich 2: capture & finalize ---
sealed interface CaptureDecision {
    data class Captured(val receipt: Receipt) : CaptureDecision
    data class CaptureFailed(val reason: String) : CaptureDecision
}

fun decideCapture(authResult: AuthResult, order: Order): CaptureDecision {
    if (!authResult.success) return CaptureFailed("Auth failed: ${authResult.error}")
    return Captured(Receipt(orderId = order.id, amount = order.total, authCode = authResult.code))
}

// --- Orchestrator ---
fun ProcessPayment(
    orderRepo: OrderRepository,
    paymentGateway: PaymentGateway,
    receiptRepo: ReceiptRepository
): suspend (PaymentRequest) -> HttpResult = { request ->
    // === Sandwich 1: authorize ===
    // 🔴 read
    val order = orderRepo.findById(request.orderId)
        ?: return@ProcessPayment HttpResult.NotFound("Order not found")
    val balance = paymentGateway.getBalance(request.paymentMethod)
    // 🟢 decide
    val authDecision = decideAuth(order, request.paymentMethod, balance)
    // 🔴 write
    val authResult = when (authDecision) {
        is Authorized -> paymentGateway.authorize(authDecision.authCode, authDecision.amount)
        is Declined -> return@ProcessPayment HttpResult.BadRequest(authDecision.reason)
    }

    // === Sandwich 2: capture ===
    // 🟢 decide (uses result of previous write)
    val captureDecision = decideCapture(authResult, order)
    // 🔴 write
    when (captureDecision) {
        is Captured -> {
            receiptRepo.save(captureDecision.receipt)
            orderRepo.markPaid(order.id)
            HttpResult.Ok(captureDecision.receipt)
        }
        is CaptureFailed -> {
            paymentGateway.void(authResult.code)
            HttpResult.Conflict(captureDecision.reason)
        }
    }
}
```

**When:** payment flows, multi-step workflows, operations where write result affects next decision.
**Warning:** if you reach level 4, consider whether this should be 2 separate slices + domain events.

---

## Choosing the Right Level

```
Does the slice have business logic?
├── NO → Level 1 (Direct Query)
├── YES, but linear flow, no branching?
│   └── Level 2 (Transaction Script)
├── YES, with branching outcomes?
│   └── Level 3 (Impureim Sandwich) ← default for commands
└── YES, and write result feeds next decision?
    └── Level 4 (Multi-step) — consider splitting into 2 slices
```

## File Organization Within a Slice

### Simple slice (levels 1-2): single file

```
orders/getOrder/
└── GetOrder.kt        ← everything in one file
```

### Standard slice (level 3): single file or 2 files

```
orders/placeOrder/
├── PlaceOrder.kt      ← handler + decision sealed class + pure logic
└── PlaceOrderTest.kt  ← tests for pure logic (optional — can live in test/)
```

Or if the pure logic is substantial:

```
orders/placeOrder/
├── PlaceOrder.kt      ← handler (sandwich)
└── OrderLogic.kt      ← pure functions + sealed class decisions
```

### Complex slice (level 4): 2-3 files max

```
payments/processPayment/
├── ProcessPayment.kt  ← orchestrator handler
├── AuthLogic.kt       ← sub-sandwich 1: pure logic + decisions
└── CaptureLogic.kt    ← sub-sandwich 2: pure logic + decisions
```

**Rule:** if a slice grows beyond 3 files, it probably needs to be split into separate slices.

## Test Strategy Per Level

| Level | Unit tests | Integration tests |
|---|---|---|
| 1. Direct Query | None (no pure logic) | Query returns correct data |
| 2. Transaction Script | Small pure helpers | Full flow against test DB |
| 3. Impureim Sandwich | Pure function — all branches | Handler against test DB |
| 4. Multi-step | Each sub-decision function | Full orchestrated flow |

### Unit test template (pure function — no mocks)

```kotlin
@Test
fun `out of stock when inventory insufficient`() {
    val items = listOf(OrderItem(ProductId("A"), quantity = 5))
    val stock = mapOf(ProductId("A") to 2)
    val prices = mapOf(ProductId("A") to BigDecimal("10.00"))

    val result = buildOrder(items, stock, prices, LoyaltyTier.NONE, ShippingZone.LOCAL)

    assertIs<OutOfStock>(result)
    assertEquals(listOf(ProductId("A")), result.missing)
}
```
