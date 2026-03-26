# From Dependency Injection to Dependency Rejection

> **Оригінал**: [blog.ploeh.dk/2017/01/27/from-dependency-injection-to-dependency-rejection](https://blog.ploeh.dk/2017/01/27/from-dependency-injection-to-dependency-rejection/)
> **Дата**: 27 січня 2017
> **Автор**: Mark Seemann

---

## Головна ідея

Серія з 4 статей, де Марк показує: проблему, яку в OOP розв'язують через Dependency Injection, у FP розв'язують **зовсім інакше** — через Impureim Sandwich. Partial application / currying — це DI, але це **не робить код чистим**.

## Серія статей

| # | Стаття | Ключовий висновок |
|---|--------|-------------------|
| 1 | [DI is passing an argument](https://blog.ploeh.dk/2017/01/27/dependency-injection-is-passing-an-argument/) | Constructor Injection = передача аргументу через конструктор |
| 2 | [Partial application is DI](https://blog.ploeh.dk/2017/01/30/partial-application-is-dependency-injection/) | Часткове застосування структурно ≡ DI. Але це **не робить код чистим** |
| 3 | [Dependency rejection](https://blog.ploeh.dk/2017/02/02/dependency-rejection/) | Замість інжекту — відхилити залежність: передати дані, а не функцію |
| 4 | [Pure interactions](https://blog.ploeh.dk/2017/07/10/pure-interactions/) | Для складних випадків — Free monad |

## Еволюція: від OOP до FP (Kotlin)

### Крок 1: Класичний OOP з DI

```kotlin
class OrderService(
    private val inventoryRepo: InventoryRepository,  // ← інжектована залежність
    private val orderRepo: OrderRepository           // ← інжектована залежність
) {
    suspend fun placeOrder(request: PlaceOrderRequest): OrderId? {
        // impure: виклик інжектованих залежностей всередині "бізнес-логіки"
        val stock = inventoryRepo.checkStock(request.productIds())
        val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }

        if (outOfStock.isNotEmpty()) return null

        val order = Order(items = request.items, total = calculateTotal(request))
        return orderRepo.create(order)
    }
}
```

Проблема: `placeOrder` **виглядає** як бізнес-логіка, але вона impure — залежить від repository.

### Крок 2: Curried function (виглядає FP, але НЕ чисте)

```kotlin
// Curried: біндимо repos, повертаємо lambda
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository
): suspend (PlaceOrderRequest) -> OrderId? = { request ->
    val stock = inventoryRepo.checkStock(request.productIds())  // ← impure всередині!
    val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }

    if (outOfStock.isNotEmpty()) null
    else orderRepo.create(Order(request.items, calculateTotal(request)))  // ← impure!
}
```

Це **структурно ідентично** DI з кроку 1. Каррінг ≡ Constructor Injection. Код **не став чистішим** — ми просто замінили class на функцію.

### Крок 3: Dependency Rejection (справжнє FP)

```kotlin
// 🟢 PURE: приймає тільки дані, повертає рішення
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

Тепер `buildOrder` — **справді чиста**: приймає `Map` і `Request`, повертає `OrderDecision`. IO відбувається зовні.

### Крок 4: Композиція на edge (Impureim Sandwich)

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    // 🟢 pure: валідація
    val validated = validateOrder(request)
        ?: return HttpResult.BadRequest("Invalid order")

    // 🔴 impure: read
    val stock = inventoryRepo.checkStock(validated.productIds())

    // 🟢 pure: business decision
    val decision = buildOrder(stock, validated)

    // 🔴 impure: write
    return when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            HttpResult.Created(id)                    // 🟢 pure: translate
        }
        is OutOfStock ->
            HttpResult.Conflict(decision.missing)     // 🟢 pure: translate
    }
}
```

## Ключовий інсайт

> **Currying / Partial application ≡ DI** — структурно еквівалентні. Але DI робить все impure, тому curried функція з impure залежністю — теж impure.
>
> **Dependency Rejection** — замість того щоб біндити функцію "як дістати дані", просто **передай самі дані**.

```
OOP / Currying:   bind Repository → call inside logic (impure!)
FP:               call Repository outside → pass data to pure logic
```

### Крок 2 + Крок 4 — поєднання currying з sandwich

Currying можна використовувати **правильно** — як composition root, де lambda виконує сендвіч:

```kotlin
// Біндимо інфраструктуру, але ВСЕРЕДИНІ lambda — чіткий sandwich
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 pure: validate
    val validated = validateOrder(request) ?: return@placeOrder HttpResult.BadRequest("Invalid")

    // 🔴 impure: read (repo call)
    val stock = inventoryRepo.checkStock(validated.productIds())

    // 🟢 pure: decide (NO repo inside!)
    val decision = buildOrder(stock, validated)

    // 🔴 impure: write (repo call)
    when (decision) {
        is Fulfillable -> HttpResult.Created(orderRepo.create(decision.order))
        is OutOfStock -> HttpResult.Conflict(decision.missing)
    }
}
```

Різниця з кроком 2: **pure функція `buildOrder` НЕ викликає repo** — вона отримує дані як аргументи. Каррінг тут — лише спосіб зв'язати залежності, а не спосіб приховати impurity.

## Зв'язок з іншими статтями

- → [Impureim Sandwich](02-impureim-sandwich.md) — паттерн, який народився з цієї серії
- → [Decoupling Decisions from Effects](03-decoupling-decisions-from-effects.md) — попередня стаття з тією ж ідеєю
- → [Ports and Adapters](07-ports-and-adapters.md) — архітектурне обґрунтування
- → [Asynchronous Injection](09-asynchronous-injection.md) — як async ускладнює DI
