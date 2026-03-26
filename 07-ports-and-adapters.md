# Functional Architecture is Ports and Adapters

> **Оригінал**: [blog.ploeh.dk/2016/03/18/functional-architecture-is-ports-and-adapters](https://blog.ploeh.dk/2016/03/18/functional-architecture-is-ports-and-adapters/)
> **Дата**: 18 березня 2016
> **Автор**: Mark Seemann

---

## Головна ідея

Функціональна архітектура **автоматично** стає Ports & Adapters (Hexagonal). В OOP ця архітектура вимагає величезних зусиль (книга DI — 500 стор., APPP — 700 стор., DDD — 500 стор.), у FP вона з'являється **безкоштовно**.

## OOP: Сізіфова праця

В OOP Ports & Adapters потребує:
- Інтерфейсів для кожної залежності
- Dependency Injection контейнерів
- Постійної дисципліни

> *"It requires much diligence, and if you look away for a moment, the boulder rolls downhill again."*

## FP: Pit of Success

Якщо дотримуватись функціонального закону (pure не викликає impure), архітектура **сама** складається в Hexagonal:

```
┌──────────────────────────────────────────────┐
│            Impure Shell (Ports)               │
│  HTTP routes, Kafka consumers, Cron jobs      │
│                                               │
│  ┌────────────────────────────────────────┐   │
│  │         Pure Core (Domain)             │   │
│  │                                        │   │
│  │  buildOrder()                          │   │
│  │  validateOrder()                       │   │
│  │  calculateShipping()                   │   │
│  │  decideDiscount()                      │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  inventoryRepo.checkStock()     ← Adapter     │
│  orderRepo.create()             ← Adapter     │
│  paymentService.charge()        ← Adapter     │
└──────────────────────────────────────────────┘
```

## Ключовий рефакторинг (Kotlin)

### v1: "достатньо функціональний" — curried, але НЕ чистий

```kotlin
// checkStock — impure функція, передана через currying
fun BuildOrder(
    checkStock: suspend (List<ProductId>) -> Map<ProductId, Int>  // ← impure залежність!
): suspend (PlaceOrderRequest) -> OrderDecision = { request ->
    val stock = checkStock(request.productIds())  // ← impure виклик всередині!
    val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }

    if (outOfStock.isNotEmpty()) OutOfStock(outOfStock.map { it.productId })
    else Fulfillable(Order(request.items, calculateTotal(request)))
}
```

Виглядає функціонально (curried!), але `checkStock` ходить у БД → вся функція impure.

**Haskell-тест**: `(List<ProductId> -> IO Map) ≠ (List<ProductId> -> Map)` — типи не збігаються, компілятор відмовить.

### v2: справді функціональний — передай значення, а не функцію

```kotlin
// 🟢 PURE: приймає тільки дані
fun buildOrder(
    stock: Map<ProductId, Int>,
    request: PlaceOrderRequest
): OrderDecision {
    val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }

    return if (outOfStock.isNotEmpty()) OutOfStock(outOfStock.map { it.productId })
    else Fulfillable(Order(request.items, calculateTotal(request)))
}
```

**Замість функції-залежності — передай значення.** IO залишається на edge:

```kotlin
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    val stock = inventoryRepo.checkStock(request.productIds())   // 🔴 impure: adapter

    val decision = buildOrder(stock, request)                    // 🟢 pure: domain

    return when (decision) {                                     // 🔴 impure: adapter
        is Fulfillable -> HttpResult.Created(orderRepo.create(decision.order))
        is OutOfStock -> HttpResult.Conflict(decision.missing)
    }
}
```

## Як це виглядає в awesome-flow-service

```
┌──────────────────────────────────────────────────────────┐
│  Impure Shell                                             │
│                                                           │
│  Routes (Ktor):       POST /orders, GET /orders/:id       │  ← Ports
│  Kafka consumers:     order.events, payment.events        │  ← Ports
│  Cron:                daily price sync, inventory check    │  ← Ports
│                                                           │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Pure Core                                         │   │
│  │                                                    │   │
│  │  buildOrder(stock, request) → OrderDecision        │   │
│  │  calculateTotal(items, prices) → BigDecimal        │   │
│  │  loyaltyDiscount(tier, subtotal) → BigDecimal      │   │
│  │  shippingCost(zone, weight) → BigDecimal           │   │
│  │  validateOrder(request) → ValidatedOrder?          │   │
│  │  partitionUpdates(items, existing) → Pair          │   │
│  │  decideRefund(order, reason) → RefundDecision      │   │
│  └────────────────────────────────────────────────────┘   │
│                                                           │
│  inventoryRepo.checkStock()          ← Adapter (DB)       │
│  orderRepo.create() / .findById()    ← Adapter (DB)       │
│  paymentService.charge() / .refund() ← Adapter (Stripe)   │
│  shippingService.createLabel()       ← Adapter (FedEx)    │
│  emailService.send()                 ← Adapter (SES)      │
└──────────────────────────────────────────────────────────┘
```

Кожна pure-функція **не знає** про існування БД, Stripe чи FedEx. Вона працює з даними. Adapters живуть на edge і обслуговують pure core.

## Практичний тест: чи це справді Hexagonal?

Запитай себе: **чи можу я замінити PostgreSQL на HashMap і все працюватиме?**

```kotlin
// Якщо pure core не знає про DB — так:
val stock = hashMapOf(ProductId("A") to 10, ProductId("B") to 0)
val decision = buildOrder(stock, request)  // працює без БД

// Якщо buildOrder всередині викликає repo — ні, це не Hexagonal
```

## Зв'язок з іншими статтями

- → [Functional Architecture: a Definition](01-functional-architecture.md) — формальне визначення
- → [Impureim Sandwich](02-impureim-sandwich.md) — конкретний паттерн
- → [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — деталі рефакторингу
