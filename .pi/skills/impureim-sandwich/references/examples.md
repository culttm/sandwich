# End-to-End Examples

Complete sandwich implementations for common scenarios.

## 1. Place Order (Recawr — full flow)

```kotlin
// === Sealed decisions ===

sealed interface OrderDecision {
    data class Fulfillable(
        val order: Order,
        val reservations: List<Reservation>
    ) : OrderDecision
    data class OutOfStock(val missing: List<ProductId>) : OrderDecision
    data class PriceChanged(val newPrices: Map<ProductId, BigDecimal>) : OrderDecision
}

// === Pure functions ===

fun validateOrderRequest(dto: PlaceOrderDto): ValidatedOrder? {
    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull() ?: return null
    val items = dto.items.mapNotNull { parseOrderItem(it) }
    if (items.isEmpty()) return null
    return ValidatedOrder(items, date)
}

fun parseOrderItem(dto: OrderItemDto): OrderItem? {
    if (dto.quantity <= 0) return null
    if (dto.productId.isBlank()) return null
    return OrderItem(ProductId(dto.productId), dto.quantity)
}

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
    val shipping = shippingCost(shippingZone, subtotal - discount)

    return Fulfillable(
        order = Order(items, subtotal, discount, shipping, total = subtotal - discount + shipping),
        reservations = items.map { Reservation(it.productId, it.quantity) }
    )
}

// === Edge (sandwich) ===

suspend fun placeOrder(dto: PlaceOrderDto): HttpResult {
    // 🟢 parse
    val validated = validateOrderRequest(dto)
        ?: return HttpResult.BadRequest("Invalid order")

    // 🔴 read
    val stock = inventoryRepo.checkStock(validated.productIds())
    val prices = pricingRepo.getCurrentPrices(validated.productIds())
    val customer = customerRepo.findById(dto.customerId)

    // 🟢 decide
    val decision = buildOrder(
        validated.items, stock, prices,
        customer.loyaltyTier, customer.shippingZone
    )

    // 🔴 write
    return when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            paymentService.charge(customer.paymentMethod, decision.order.total)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
        is PriceChanged -> HttpResult.Conflict("Prices changed: ${decision.newPrices}")
    }
}
```

### Tests for the pure core

```kotlin
@Test
fun `build order — all in stock, correct prices`() {
    val items = listOf(OrderItem(ProductId("A"), 2), OrderItem(ProductId("B"), 1))
    val stock = mapOf(ProductId("A") to 10, ProductId("B") to 5)
    val prices = mapOf(ProductId("A") to "25.00".toBigDecimal(), ProductId("B") to "40.00".toBigDecimal())

    val decision = buildOrder(items, stock, prices, LoyaltyTier.SILVER, ShippingZone.DOMESTIC)

    assertIs<Fulfillable>(decision)
    assertEquals("85.75".toBigDecimal(), decision.order.total)  // with discount + shipping
}

@Test
fun `build order — insufficient stock`() {
    val items = listOf(OrderItem(ProductId("A"), 100))
    val stock = mapOf(ProductId("A") to 2)
    val prices = mapOf(ProductId("A") to "10.00".toBigDecimal())

    val decision = buildOrder(items, stock, prices, LoyaltyTier.NONE, ShippingZone.DOMESTIC)

    assertIs<OutOfStock>(decision)
    assertEquals(listOf(ProductId("A")), decision.missing)
}
```

No mocks. No runTest. No coroutines. Just data in → decision out.

---

## 2. Apply Discount (sealed class decisions)

```kotlin
// === Sealed decisions ===

sealed class DiscountDecision {
    data class ApplyDiscount(val rate: BigDecimal, val reason: String) : DiscountDecision()
    data object NoDiscount : DiscountDecision()
}

// === Pure ===

fun decideDiscount(order: Order, history: CustomerHistory): DiscountDecision {
    if (history.totalOrders >= 50)
        return ApplyDiscount("0.15".toBigDecimal(), "loyalty_gold")
    if (history.totalOrders >= 10)
        return ApplyDiscount("0.05".toBigDecimal(), "loyalty_silver")
    if (order.total > "500".toBigDecimal())
        return ApplyDiscount("0.10".toBigDecimal(), "large_order")
    return NoDiscount
}

// === Edge ===

suspend fun applyOrderDiscount(orderId: OrderId) {
    // 🔴 read
    val order = orderRepo.findById(orderId)
    val history = customerRepo.getHistory(order.customerId)

    // 🟢 decide
    val decision = decideDiscount(order, history)

    // 🔴 write
    when (decision) {
        is ApplyDiscount -> {
            orderRepo.updateTotal(orderId, order.total * (BigDecimal.ONE - decision.rate))
            auditLog.record(orderId, "discount_applied", decision.reason)
        }
        is NoDiscount -> { /* nothing */ }
    }
}
```

---

## 3. Confirm Signup (simple 3-layer)

```kotlin
sealed interface SignupDecision {
    data class Verified(val account: Account) : SignupDecision
    data object AlreadyUsed : SignupDecision
}

// 🟢 Pure
fun verifySignup(pending: PendingSignup): SignupDecision =
    if (pending.usedAt != null) AlreadyUsed
    else Verified(Account(email = pending.email, name = pending.name))

// 🔴 Edge
suspend fun confirmSignup(token: String): SignupResult {
    val pending = signupRepo.findByToken(token)
        ?: return SignupResult.TokenExpired

    val decision = verifySignup(pending)

    return when (decision) {
        is Verified -> {
            accountRepo.activate(decision.account)
            SignupResult.Activated(decision.account.id)
        }
        is AlreadyUsed -> SignupResult.Conflict("Token already used")
    }
}
```

---

## 4. Bulk Update (Recawr — avoiding write-before-calculate)

```kotlin
// ❌ WRONG: write before calculate
suspend fun bulkUpdatePricesWrong(items: List<PriceUpdate>): BulkResult {
    val results = items.map { runCatching { productRepo.updatePrice(it.productId, it.newPrice) } }
    return results.fold(BulkResult.empty()) { state, res ->
        res.fold(onSuccess = { state.withUpdated(it) }, onFailure = { state.withError(it) })
    }
}

// ✅ RIGHT: read → calculate → write
suspend fun bulkUpdatePrices(items: List<PriceUpdate>): BulkResult {
    // 🔴 read
    val existing = productRepo.findByIds(items.map { it.productId })

    // 🟢 calculate
    val (toUpdate, notFound) = partitionUpdates(items, existing)

    // 🔴 write
    toUpdate.forEach { productRepo.updatePrice(it.productId, it.newPrice) }

    return BulkResult(updated = toUpdate.map { it.productId }, notFound = notFound.map { it.productId })
}

// 🟢 Pure
fun partitionUpdates(
    items: List<PriceUpdate>,
    existing: Set<ProductId>
): Pair<List<PriceUpdate>, List<PriceUpdate>> =
    items.partition { it.productId in existing }
```

---

## 5. Config Loading (3 ways to model decisions)

### With sealed class

```kotlin
sealed class ConfigAction {
    data class LoadFromFile(val path: String) : ConfigAction()
    data class UseDefaults(val config: UserConfig) : ConfigAction()
}

fun decideConfigSource(path: String, fileExists: Boolean): ConfigAction =
    if (fileExists) LoadFromFile(path)
    else UseDefaults(UserConfig.default())
```

### With nullable

```kotlin
fun loadUserConfig(path: String): UserConfig {
    return path
        .takeIf { File(it).exists() }       // 🔴 impure
        ?.let { File(it).readText() }        // 🔴 impure
        ?.let { UserConfig.parse(it) }       // 🟢 pure
        ?: UserConfig.default()              // 🟢 pure
}
```

### With Result

```kotlin
fun loadUserConfig(path: String): UserConfig {
    val result = if (File(path).exists()) Result.success(path)
                 else Result.failure(FileNotFoundException(path))
    return result.fold(
        onSuccess = { UserConfig.parse(File(it).readText()) },
        onFailure = { UserConfig.default() }
    )
}
```

---

## Architecture Visualization

```
┌────────────────────────────────────────────────────────┐
│  Impure Shell                                           │
│                                                         │
│  Routes:    POST /orders, GET /orders/:id               │ ← Ports
│  Kafka:     order.events, payment.events                │ ← Ports
│  Cron:      daily price sync                            │ ← Ports
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Pure Core                                       │   │
│  │                                                  │   │
│  │  buildOrder(stock, request) → OrderDecision      │   │
│  │  decideDiscount(order, history) → DiscountDecision│  │
│  │  validateOrder(now, dto) → String                │   │
│  │  calculateTotal(items, prices) → BigDecimal      │   │
│  │  loyaltyDiscount(tier, subtotal) → BigDecimal    │   │
│  │  shippingCost(zone, weight) → BigDecimal         │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  inventoryRepo.checkStock()         ← Adapter (DB)      │
│  orderRepo.create()                 ← Adapter (DB)      │
│  paymentService.charge()            ← Adapter (Stripe)   │
│  emailService.send()                ← Adapter (SES)      │
└────────────────────────────────────────────────────────┘
```

**Hexagonal test:** can I replace PostgreSQL with HashMap and everything works?

```kotlin
val stock = mapOf(ProductId("A") to 10)
val decision = buildOrder(stock, request)  // works without DB
```

If `buildOrder` calls a repository inside → it's NOT hexagonal.
