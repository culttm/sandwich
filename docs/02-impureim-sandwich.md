# Impureim Sandwich

> **Оригінал**: [blog.ploeh.dk/2020/03/02/impureim-sandwich](https://blog.ploeh.dk/2020/03/02/impureim-sandwich/)
> **Дата**: 2 березня 2020
> **Автор**: Mark Seemann

---

## Головна ідея

Стаття дає **ім'я та визначення** паттерну, який Марк використовує з 2017 року: **impure → pure → impure** сендвіч.

## Паттерн

```
┌────────────────────────────────────┐
│ 🔴 1. Impure: зібрати дані        │  ← DB.query, HTTP request, File.read
├────────────────────────────────────┤
│ 🟢 2. Pure: обчислити результат   │  ← бізнес-логіка, валідація, рішення
├────────────────────────────────────┤
│ 🔴 3. Impure: записати результат  │  ← DB.insert, HTTP response, Email
└────────────────────────────────────┘
```

**Хліб** (impure шари) — це affordance, як у сендвічі Графа Сендвіча. Він дозволяє "тримати" чисту функцію, не "забруднюючи" руки.

## Приклади (Kotlin)

### Базовий сендвіч — оформлення замовлення

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    // 🔴 impure: читаємо з БД
    val inventory = inventoryRepo.getStock(request.items.map { it.productId })

    // 🟢 pure: перевіряємо наявність і рахуємо ціну
    val decision = fulfillOrder(inventory, request)

    // 🔴 impure: записуємо результат
    return when (decision) {
        is Fulfillable -> {
            orderRepo.create(decision.order)
            HttpResult.Created(decision.order.id)
        }
        is OutOfStock -> HttpResult.Conflict("Items out of stock: ${decision.missing}")
    }
}
```

### Curried-стиль

```kotlin
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository,
    pricing: PricingRules
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🔴 impure: read
    val inventory = inventoryRepo.getStock(request.productIds())

    // 🟢 pure: decide
    val decision = fulfillOrder(pricing, inventory, request)

    // 🔴 impure: write
    when (decision) {
        is Fulfillable -> {
            orderRepo.create(decision.order)
            HttpResult.Created(decision.order.id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
    }
}

// 🟢 Pure function — легко тестувати
fun fulfillOrder(
    pricing: PricingRules,
    inventory: Map<ProductId, Int>,
    request: PlaceOrderRequest
): OrderDecision {
    val missing = request.items.filter { item ->
        (inventory[item.productId] ?: 0) < item.quantity
    }
    if (missing.isNotEmpty())
        return OutOfStock(missing.map { it.productId })

    val total = request.items.sumOf { item ->
        pricing.priceFor(item.productId) * item.quantity
    }
    return Fulfillable(Order(items = request.items, total = total))
}
```

### Реєстрація з email-верифікацією

```kotlin
suspend fun confirmSignup(token: String): SignupResult {
    // 🔴 impure: зчитуємо pending signup за токеном
    val pending = signupRepo.findByToken(token)
        ?: return SignupResult.TokenExpired

    // 🟢 pure: бізнес-рішення
    val decision = verifySignup(pending)

    // 🔴 impure: активація або відмова
    return when (decision) {
        is Verified -> {
            accountRepo.activate(decision.account)
            SignupResult.Activated(decision.account.id)
        }
        is AlreadyUsed -> SignupResult.Conflict("Token already used")
    }
}
```

## Назва

**Impureim** = **im**pure / **pure** / **im**pure.

Єдина відома анаграма — **imperium**. Вимовляється як *"impurium sandwich"*.

## Коли НЕ працює

Марк визнає: паттерн не завжди можна застосувати:

> *"It's my experience that it's conspicuously often possible to implement an impure/pure/impure sandwich."*

Але **не завжди**. Для складних випадків з каскадними IO-викликами існують інші підходи (Free monad, Effect systems).

## Подальший розвиток

- [What's a sandwich?](04-whats-a-sandwich.md) — розширення до 5 шарів
- [Recawr Sandwich](05-recawr-sandwich.md) — спеціалізація Read/Calculate/Write
- [A conditional sandwich example](https://blog.ploeh.dk/2022/02/14/a-conditional-sandwich-example/)
- [A restaurant sandwich](https://blog.ploeh.dk/2024/12/16/a-restaurant-sandwich/)

## Зв'язок з іншими статтями

- ← [Functional Architecture](01-functional-architecture.md) — теоретична основа
- → [What's a sandwich?](04-whats-a-sandwich.md) — реалістичні обмеження
- → [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — серія, де паттерн вперше з'явився
