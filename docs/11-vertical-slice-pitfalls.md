# Vertical Slice Architecture: Підводні камені

> **Джерела**:
> - [jimmybogard.com/vertical-slice-architecture](https://www.jimmybogard.com/vertical-slice-architecture/) — Jimmy Bogard, 2018
> - [milanjovanovic.tech/blog/vertical-slice-architecture-structuring-vertical-slices](https://www.milanjovanovic.tech/blog/vertical-slice-architecture-structuring-vertical-slices) — Milan Jovanovic, 2024
> - [blog.ndepend.com/vertical-slice-architecture-in-asp-net-core](https://blog.ndepend.com/vertical-slice-architecture-in-asp-net-core/) — NDepend Blog, 2025
> - [ricofritzsche.me/why-vertical-slices-wont-evolve-from-clean-architecture](https://ricofritzsche.me/why-vertical-slices-wont-evolve-from-clean-architecture/) — Rico Fritzsche, 2025

---

## Головна ідея

VSA — не срібна куля. Вона вирішує проблеми layered architecture, але створює **свої**: дублювання коду, розмитий shared logic, втрата консистентності, підвищені вимоги до команди. Ця стаття — про те, коли і чому VSA ламається.

## 1. Дублювання коду

Найчастіша критика. Коли кожен slice ізольований, однакова логіка з'являється в кількох місцях.

### Приклад: валідація в двох slices (Kotlin)

```kotlin
// orders/placeOrder/PlaceOrder.kt
fun validateItems(items: List<OrderItemDto>): List<OrderItem>? {
    if (items.isEmpty()) return null
    return items.map { dto ->
        if (dto.quantity <= 0) return null
        if (dto.productId.isBlank()) return null
        OrderItem(ProductId(dto.productId), dto.quantity)
    }
}

// orders/updateOrder/UpdateOrder.kt
fun validateItems(items: List<OrderItemDto>): List<OrderItem>? {
    if (items.isEmpty()) return null                        // ← копі-паста
    return items.map { dto ->
        if (dto.quantity <= 0) return null                  // ← копі-паста
        if (dto.productId.isBlank()) return null            // ← копі-паста
        OrderItem(ProductId(dto.productId), dto.quantity)
    }
}
```

### Два табори

| Позиція | Аргумент |
|---------|----------|
| **"Duplication is cheaper than the wrong abstraction"** | Передчасна абстракція зв'язує slices і створює God-object. Краще дублювати і чекати, поки паттерн проявиться |
| **"More code bloat, more confusion"** | Дублювання плодить баги. Виправив в одному slice — забув в іншому. Junior-розробники губляться |

### Правило

> Дублювання між slices — **нормально на старті**. Але коли один і той самий код з'являється втретє — це сигнал до extract.

### Рішення: витягнути в shared pure function

```kotlin
// common/validation/OrderValidation.kt — shared, але pure
fun parseOrderItems(dtos: List<OrderItemDto>): List<OrderItem>? {
    if (dtos.isEmpty()) return null
    return dtos.map { dto ->
        if (dto.quantity <= 0) return null
        if (dto.productId.isBlank()) return null
        OrderItem(ProductId(dto.productId), dto.quantity)
    }
}

// orders/placeOrder/PlaceOrder.kt
val items = parseOrderItems(request.items) ?: return HttpResult.BadRequest("Invalid items")

// orders/updateOrder/UpdateOrder.kt
val items = parseOrderItems(request.items) ?: return HttpResult.BadRequest("Invalid items")
```

Ключове: shared код — це **pure функція без залежностей**. Не service, не repository, не абстракція.

## 2. Shared logic перетворюється на новий service layer

Найнебезпечніший антипаттерн. `common/` папка поступово росте і стає тим самим горизонтальним шаром, від якого тікали.

### Як це відбувається

```
Місяць 1:    common/
               └── DateUtils.kt              ← нормально

Місяць 3:    common/
               ├── DateUtils.kt
               ├── OrderValidation.kt         ← ок
               └── PricingCalculator.kt        ← ще ок

Місяць 6:    common/
               ├── DateUtils.kt
               ├── OrderValidation.kt
               ├── PricingCalculator.kt
               ├── InventoryService.kt         ← 🚩 service!
               ├── CustomerService.kt          ← 🚩 service!
               ├── NotificationHelper.kt       ← 🚩 helper!
               └── OrderRepository.kt          ← 🚩 повернулись до layered
```

> *"Just don't call one feature from another. Refactor!"* — Jimmy Bogard

### Правило: що МОЖНА класти в common

| ✅ Дозволено в common | ❌ Заборонено в common |
|------------------------|------------------------|
| Pure функції (парсинг, калькуляції) | Services з бізнес-логікою |
| Value objects / DTOs | Repositories |
| Extension functions | Функції з side effects |
| Константи, enum'и | Orchestration / workflow |

### Лакмусовий тест

> Якщо файл у `common/` має `suspend` — це 🚩.
> Якщо файл у `common/` приймає repository як аргумент — це 🚩.
> Якщо файл у `common/` росте більше ніж на 50 рядків — перевір, чи не domain logic це.

## 3. Feature Factory: втрата цілісності домену

Коли кожна фіча будується ізольовано, можна втратити загальну картину домену.

### Приклад: три slices — три різні моделі Order

```kotlin
// orders/placeOrder/PlaceOrder.kt
data class Order(val items: List<OrderItem>, val total: BigDecimal)

// orders/getOrder/GetOrder.kt
data class OrderResponse(val id: String, val status: String, val total: BigDecimal)

// orders/cancelOrder/CancelOrder.kt
data class OrderForCancel(val id: OrderId, val status: OrderStatus, val createdAt: Instant)
```

Три різні view на одну сутність — нормально для DTO/Response. **Не нормально**, коли бізнес-правила дублюються або суперечать одне одному:

```kotlin
// placeOrder: мінімальне замовлення — 10$
if (total < BigDecimal("10.00")) return InvalidRequest("Min order is $10")

// updateOrder: мінімальне замовлення — 15$ (хтось забув синхронізувати)
if (total < BigDecimal("15.00")) return InvalidRequest("Min order is $15")
```

### Рішення: shared domain rules

```kotlin
// domain/OrderRules.kt — pure, shared бізнес-правила
object OrderRules {
    val MIN_ORDER_TOTAL = BigDecimal("10.00")

    fun validateMinimumTotal(total: BigDecimal): Boolean =
        total >= MIN_ORDER_TOTAL
}
```

Slices використовують `OrderRules`, але **не залежать один від одного**.

## 4. Команда не готова

VSA працює **тільки** якщо команда вміє:

### Розпізнавати code smells

```kotlin
// 🚩 Slice робить занадто багато — потрібен extract
fun PlaceOrder(...): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    val items = parseItems(request.items) ?: return@PlaceOrder HttpResult.BadRequest("...")
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val prices = pricingRepo.getCurrentPrices(items.map { it.productId })
    val customer = customerRepo.findById(request.customerId)
    val missing = items.filter { (stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return@PlaceOrder HttpResult.Conflict("...")
    val priceChanges = items.filter { prices[it.productId] != it.expectedPrice }
    if (priceChanges.isNotEmpty()) return@PlaceOrder HttpResult.Conflict("...")
    val subtotal = items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
    val loyaltyDiscount = when {
        customer.totalOrders >= 50 -> subtotal * BigDecimal("0.15")
        customer.totalOrders >= 10 -> subtotal * BigDecimal("0.05")
        else -> BigDecimal.ZERO
    }
    val shipping = when (customer.shippingZone) {
        ShippingZone.LOCAL -> BigDecimal("5.00")
        ShippingZone.DOMESTIC -> BigDecimal("15.00")
        ShippingZone.INTERNATIONAL -> BigDecimal("30.00")
    }
    val total = subtotal - loyaltyDiscount + shipping
    val order = Order(items, subtotal, loyaltyDiscount, shipping, total)
    val id = orderRepo.create(order)
    inventoryRepo.reserve(items.map { Reservation(it.productId, it.quantity) })
    HttpResult.Created(id)
}
```

Тут pure логіка (перевірка stock, розрахунок знижки, shipping) змішана з impure (repo calls). Команда повинна **бачити** це і рефакторити в sandwich:

```kotlin
// ✅ Після рефакторингу: чіткий sandwich
fun PlaceOrder(...): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 parse
    val items = parseItems(request.items) ?: return@PlaceOrder HttpResult.BadRequest("...")

    // 🔴 read
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val prices = pricingRepo.getCurrentPrices(items.map { it.productId })
    val customer = customerRepo.findById(request.customerId)

    // 🟢 decide — витягнуто в окрему pure функцію
    val decision = buildOrder(items, stock, prices, customer.loyaltyTier, customer.shippingZone)

    // 🔴 write
    when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
        is PriceChanged -> HttpResult.Conflict("Prices changed")
    }
}
```

### Знати коли виносити domain logic

> *"If your team does not understand when a 'service' is doing too much to push logic to the domain, this pattern is likely not for you."* — Jimmy Bogard

Ознаки що slice переріс:
- **> 80 рядків** в handler — час для extract method / extract function
- **> 3 sealed class варіанти** в рішенні — можливо, це два окремі slice
- **Той самий when/if** в кількох slices — час для shared pure function

## 5. Втрата консистентності коду

Кожен slice може обрати свій підхід — це і перевага, і проблема.

### Проблема: один застосунок — три стилі

```kotlin
// slice A: Transaction Script
fun createProduct(...) = { request ->
    db.execute("INSERT INTO products ...")
}

// slice B: Rich Domain Model
fun placeOrder(...) = { request ->
    val order = Order.create(request)
    order.validate()
    order.calculateTotal(pricingRules)
    orderRepo.save(order)
}

// slice C: якийсь "креативний" підхід
fun cancelOrder(...) = { request ->
    val service = CancelOrderService(orderRepo, notificationService, auditLog)
    service.execute(request)
}
```

> *"While the lack of code consistency between independent services is fine, it is annoying within a single solution because this hinders the principle of least surprise."* — NDepend Blog

### Рішення: конвенції команди

Не потрібно нав'язувати один підхід на все, але потрібна **анатомія slice**:

```
Конвенція команди:
1. Кожен slice — окрема папка з назвою use case
2. Handler — curried function, НЕ class
3. Pure logic — окрема fun без suspend
4. Рішення моделюються sealed class
5. Cross-cutting (auth, logging) — на edge, не в slice
```

## 6. Semantic Diffusion: "VSA" яка не є VSA

Найпоширеніша помилка — переставити папки в Clean Architecture і назвати це Vertical Slices.

### ❌ НЕ є VSA: Clean Architecture з перейменованими папками

```
src/
├── features/
│   └── orders/
│       ├── domain/
│       │   └── Order.kt
│       ├── application/
│       │   ├── OrderService.kt        ← все ще service layer!
│       │   └── OrderRepository.kt     ← все ще interface!
│       ├── infrastructure/
│       │   └── OrderRepositoryImpl.kt ← все ще DI + impl!
│       └── presentation/
│           └── OrderController.kt     ← все ще controller!
```

Це **layered architecture в папці features**. Coupling все ще горизонтальний.

> *"They flattened the structure but never fully embraced a feature-first mentality."* — Rico Fritzsche

### ✅ Справжня VSA

```
src/
├── orders/
│   ├── placeOrder/
│   │   └── PlaceOrder.kt     ← все в одному файлі: request, decision, pure fn, handler
│   ├── getOrder/
│   │   └── GetOrder.kt       ← простий query — 20 рядків
│   └── cancelOrder/
│       └── CancelOrder.kt
├── customers/
│   └── ...
└── common/
    └── validation/            ← мінімум shared pure functions
```

Різниця: **немає layers всередині feature**. Кожен slice — самодостатній, обирає свій підхід.

## 7. Тестування: інші компроміси

### Layered architecture

```
+ легко unit-тестувати через інтерфейси і mock'и
- тести крихкі, прив'язані до структури mock'ів
- багато boilerplate в тестах
```

### VSA + Impureim Sandwich

```
+ pure функції тестуються без mock'ів взагалі
+ тести стабільні — не залежать від інфраструктури
- складніше тестувати impure edge (integration tests)
- якщо sandwich зламаний — pure і impure змішані — тестувати важко
```

### Приклад: тест pure функції в slice

```kotlin
// Тест без mock'ів, без DI, без setup — просто дані
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

Якщо `buildOrder` — pure, тести прості. Якщо slice не має чіткого sandwich — тестувати стає **значно складніше**.

## Підсумок: trade-offs

```
┌──────────────────────────────────────────────────────────────┐
│              Layered Architecture                            │
│  ✅ Консистентність коду                                     │
│  ✅ Зрозумілі правила для junior'ів                          │
│  ✅ Легко тестувати через mock'и                              │
│  ❌ Feature fragmentation — одна фіча в 5 папках             │
│  ❌ Coupling між шарами                                      │
│  ❌ Абстракції заради абстракцій                             │
├──────────────────────────────────────────────────────────────┤
│              Vertical Slice Architecture                     │
│  ✅ Висока когезія — вся фіча поруч                          │
│  ✅ Гнучкість — кожен slice обирає свій підхід               │
│  ✅ Нові фічі = новий код, без побічних ефектів              │
│  ❌ Дублювання коду між slices                               │
│  ❌ Втрата консистентності                                   │
│  ❌ Потрібна зріла команда                                   │
│  ❌ common/ може стати новим service layer                   │
└──────────────────────────────────────────────────────────────┘
```

## Чеклист: чи готова ваша команда до VSA?

- [ ] Команда розпізнає code smells (Long Method, Feature Envy, Duplicate Code)
- [ ] Команда практикує рефакторинг (Extract Method, Extract Class, Move Function)
- [ ] Команда розуміє різницю між pure і impure кодом
- [ ] Є конвенції щодо анатомії slice
- [ ] Є правило для `common/` — тільки pure, тільки shared
- [ ] Code review перевіряє: чи не став slice занадто великим?

## Ключові цитати

> *"Duplication is far cheaper than the wrong abstraction."* — Sandi Metz

> *"This is going to lead to more code bloat, more code duplication, more confusion among developers."* — коментар до статті Jimmy Bogard

> *"Architecture is not a religion. It's a choice."* — Rico Fritzsche

> *"There is no one-size-fits-all approach. Layers lead to more coupling and rigidity. Slices lead to more duplication and reduced consistency."* — NDepend Blog

## Зв'язок з іншими статтями

- ← [Vertical Slice Architecture](10-vertical-slice-architecture.md) — що це і як працює
- → [Impureim Sandwich](02-impureim-sandwich.md) — як структурувати slice, щоб pure було тестованим
- → [Recawr Sandwich](05-recawr-sandwich.md) — правильна форма slice: Read → Calculate → Write
- → [Discerning Purity](08-discerning-purity.md) — як не зламати чистоту всередині slice
- → [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — чому curried function замість class з DI
