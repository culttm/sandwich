# Recawr Sandwich

> **Оригінал**: [blog.ploeh.dk/2025/01/13/recawr-sandwich](https://blog.ploeh.dk/2025/01/13/recawr-sandwich/)
> **Дата**: 13 січня 2025
> **Автор**: Mark Seemann

---

## Головна ідея

**Recawr Sandwich** — це спеціалізація Impureim Sandwich з конкретними ролями для impure шарів: **Read → Calculate → Write**. Більшість добре спроектованих сендвічів слідують цьому шаблону.

## Назва

**RECAWR** = **RE**ad **CA**lculate **WR**ite. Вимовляється як *"recover sandwich"*.

## Структура

```
┌──────────────────────────────────────┐
│ 🔴 READ      — прочитати дані        │  impure (DB, file, API)
├──────────────────────────────────────┤
│ 🟢 CALCULATE — обчислити результат  │  pure (бізнес-логіка)
├──────────────────────────────────────┤
│ 🔴 WRITE     — записати результат   │  impure (DB, response, email)
└──────────────────────────────────────┘
```

## Ключове обмеження

> Коли ви почали **писати** дані, ви **не повинні повертатись до читання** нових даних.

Це додаткове обмеження порівняно з загальним Impureim Sandwich:

```
┌──────────────────────────┐
│   Impureim Sandwiches    │  ← загальний набір
│  ┌────────────────────┐  │
│  │ Recawr Sandwiches  │  │  ← підмножина з додатковим правилом
│  └────────────────────┘  │
└──────────────────────────┘
```

## Приклад: що НЕ є Recawr (Kotlin)

```kotlin
// ❌ НЕ Recawr: processItem робить write ПЕРЕД тим, як ми порахуємо загальний результат
suspend fun bulkUpdatePrices(items: List<PriceUpdate>): BulkResult {
    // 🔴 write (занадто рано! оновлюємо ДО того, як порахували загальний результат)
    val results = items.map { item ->
        runCatching { productRepo.updatePrice(item.productId, item.newPrice) }
    }

    // 🟢 calculate (після write — запізно, якщо частина фейлиться — вже записали)
    val result = results.fold(BulkResult.empty()) { state, res ->
        res.fold(
            onSuccess = { state.withUpdated(it) },
            onFailure = { state.withError(it) }
        )
    }

    return result
}
```

Проблема: `updatePrice` робить запис **до** обчислення результату. Частина товарів оновиться, частина ні — inconsistent state.

## Приклад: як зробити Recawr (Kotlin)

```kotlin
// ✅ Recawr: спочатку Read, потім Calculate, потім Write
suspend fun bulkUpdatePrices(items: List<PriceUpdate>): BulkResult {
    // 🔴 READ: дізнаємося, що існує в БД
    val existing = productRepo.findByIds(items.map { it.productId })

    // 🟢 CALCULATE: чиста логіка, легко тестувати
    val (toUpdate, notFound) = partitionUpdates(items, existing)
    val result = BulkResult(
        updated = toUpdate.map { it.productId },
        notFound = notFound.map { it.productId },
        errors = emptyList()
    )

    // 🔴 WRITE: оновлюємо тільки те, що знайшли і перевірили
    toUpdate.forEach { productRepo.updatePrice(it.productId, it.newPrice) }

    return result
}

// 🟢 Pure: розділяємо на "можна оновити" і "не знайдено"
fun partitionUpdates(
    items: List<PriceUpdate>,
    existing: Set<ProductId>
): Pair<List<PriceUpdate>, List<PriceUpdate>> =
    items.partition { it.productId in existing }
```

## Повний приклад: awesome-flow-service — оформлення замовлення

```kotlin
// ✅ Recawr Sandwich
suspend fun placeOrder(request: PlaceOrderRequest): OrderResult {
    // 🔴 READ
    val stock = inventoryRepo.checkStock(request.productIds())
    val prices = pricingRepo.getCurrentPrices(request.productIds())
    val customer = customerRepo.findById(request.customerId)

    // 🟢 CALCULATE — чиста функція, вся бізнес-логіка тут
    val decision = buildOrder(
        items = request.items,
        stock = stock,
        prices = prices,
        loyaltyTier = customer.loyaltyTier,
        shippingZone = customer.shippingZone
    )

    // 🔴 WRITE
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

// 🟢 Pure — тестується без БД, HTTP, mock'ів
fun buildOrder(
    items: List<OrderItem>,
    stock: Map<ProductId, Int>,
    prices: Map<ProductId, BigDecimal>,
    loyaltyTier: LoyaltyTier,
    shippingZone: ShippingZone
): OrderDecision {
    // Перевірка наявності
    val missing = items.filter { (stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return OutOfStock(missing.map { it.productId })

    // Перевірка цін (ціна могла змінитись поки юзер оформлював)
    val priceChanges = items.filter { prices[it.productId] != it.expectedPrice }
    if (priceChanges.isNotEmpty()) return PriceChanged(priceChanges.associate {
        it.productId to prices[it.productId]!!
    })

    // Знижка
    val subtotal = items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
    val discount = loyaltyDiscount(loyaltyTier, subtotal)
    val shipping = shippingCost(shippingZone, subtotal - discount)

    return Fulfillable(
        order = Order(items, subtotal, discount, shipping, total = subtotal - discount + shipping),
        reservations = items.map { Reservation(it.productId, it.quantity) }
    )
}
```

## Правило великого пальця

> Більшість добре спроектованих сендвічів — це Recawr Sandwiches.

## Зв'язок з іншими статтями

- ← [Impureim Sandwich](02-impureim-sandwich.md) — загальний паттерн
- ← [What's a sandwich?](04-whats-a-sandwich.md) — обмеження паттерну
- ← [Functional Architecture](01-functional-architecture.md) — теоретична основа
