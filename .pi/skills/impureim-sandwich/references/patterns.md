# Sandwich Patterns

Three levels of the pattern, from ideal to realistic.

## 1. Ideal Impureim Sandwich (3-layer)

```
🔴 Impure — collect data
🟢 Pure   — business logic
🔴 Impure — write result
```

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    // 🔴 read
    val inventory = inventoryRepo.getStock(request.items.map { it.productId })

    // 🟢 decide
    val decision = fulfillOrder(inventory, request)

    // 🔴 write
    return when (decision) {
        is Fulfillable -> {
            orderRepo.create(decision.order)
            HttpResult.Created(decision.order.id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
    }
}
```

**When to use:** Simple operations with one read source and straightforward write.

## 2. Realistic Sandwich (5-layer)

```
🟢 Pure   — parse/validate input
🔴 Impure — read from DB
🟢 Pure   — business logic
🔴 Impure — write to DB
🟢 Pure   — translate to response
```

**Why first pure layer is inevitable:** to query DB you need to know WHAT to query. Parsing the request is pure. If JSON is malformed, you can't even query.

**Why last pure layer is inevitable:** building HTTP response from a decision is a pure transformation.

```kotlin
suspend fun placeOrder(dto: PlaceOrderDto): HttpResult {
    // 🟢 parse (pure — before any IO)
    val shippingDate = parseDate(dto.requestedDelivery)
        ?: return HttpResult.BadRequest("Invalid date")
    val items = dto.items.map { parseOrderItem(it) ?: return HttpResult.BadRequest("Invalid item") }
    if (items.isEmpty()) return HttpResult.BadRequest("Order must have items")

    // 🔴 read (now we know WHAT to query)
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val pricing = pricingRepo.getCurrentPrices(items.map { it.productId })

    // 🟢 decide (pure)
    val decision = buildOrder(items, stock, pricing, shippingDate)

    // 🔴 write
    return when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            HttpResult.Created(id)           // 🟢 translate
        }
        is OutOfStock ->
            HttpResult.Conflict(decision.message)  // 🟢 translate
    }
}
```

**Rules:**
- ✅ Add more pure layers — always safe
- ❌ Add more impure layers — Dagwood sandwich, redesign

**When to use:** Most real-world endpoints. This is the default.

## 3. Recawr Sandwich (Read → Calculate → Write)

A specialization with a stricter constraint:

> Once you start WRITING, you must NOT go back to READING new data.

```
┌──────────────────────────────────────┐
│  Impureim Sandwiches (general)       │
│  ┌────────────────────────────────┐  │
│  │  Recawr Sandwiches (stricter) │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): OrderResult {
    // 🔴 READ — all reads upfront
    val stock = inventoryRepo.checkStock(request.productIds())
    val prices = pricingRepo.getCurrentPrices(request.productIds())
    val customer = customerRepo.findById(request.customerId)

    // 🟢 CALCULATE — all logic in one pure function
    val decision = buildOrder(
        items = request.items,
        stock = stock,
        prices = prices,
        loyaltyTier = customer.loyaltyTier,
        shippingZone = customer.shippingZone
    )

    // 🔴 WRITE — execute the decision
    return when (decision) {
        is Fulfillable -> {
            val orderId = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            paymentService.charge(customer.paymentMethod, decision.order.total)
            OrderResult.Created(orderId)
        }
        is OutOfStock -> OrderResult.Rejected(decision.missing)
        is PriceChanged -> OrderResult.PriceChanged(decision.newPrices)
    }
}
```

**When to use:** Most well-designed sandwiches are Recawr. Use as default pattern. If you need to interleave reads and writes, consider if you can restructure to read everything upfront.

## Choosing a Pattern

```
Can I gather all data before logic?
├── YES → Recawr (Read → Calculate → Write)
├── MOSTLY → 5-layer (parse → read → decide → write → translate)
└── NO, I need cascading IO → Consider splitting into multiple sandwiches
    or using a saga/workflow pattern
```

## Pure Function Template

Every pure function in the sandwich follows this shape:

```kotlin
// NOT suspend. Takes data. Returns sealed decision.
fun decide(
    readData: ReadData,
    request: ValidatedRequest
): Decision {
    // all logic here — no IO, no suspend, no side effects
    return when {
        condition1 -> Decision.OptionA(...)
        condition2 -> Decision.OptionB(...)
        else -> Decision.OptionC(...)
    }
}

sealed interface Decision {
    data class OptionA(...) : Decision
    data class OptionB(...) : Decision
    data class OptionC(...) : Decision
}
```
