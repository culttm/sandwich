# Vertical Slice Architecture

> **Оригінал**: [jimmybogard.com/vertical-slice-architecture](https://www.jimmybogard.com/vertical-slice-architecture/)
> **Дата**: 19 квітня 2018
> **Автор**: Jimmy Bogard
>
> **Додатково**: [milanjovanovic.tech/blog/vertical-slice-architecture](https://www.milanjovanovic.tech/blog/vertical-slice-architecture)

---

## Головна ідея

Замість організації коду по **технічних шарах** (controllers → services → repositories), код організується по **фічах / use cases**. Кожен "вертикальний зріз" містить усе необхідне для однієї операції — від HTTP endpoint до БД. Coupling максимальний **всередині** slice, мінімальний **між** slices.

## Проблема з layered architecture

```
Layered (Clean / Onion / N-tier):

┌─────────────────────────────────────────────┐
│  Controllers    [A] [B] [C] [D] [E]        │ ← шар
├─────────────────────────────────────────────┤
│  Services       [A] [B] [C] [D] [E]        │ ← шар
├─────────────────────────────────────────────┤
│  Repositories   [A] [B] [C] [D] [E]        │ ← шар
└─────────────────────────────────────────────┘
  coupling: горизонтальний (між A,B,C в одному шарі)
  зміна фічі A → правки в 3+ шарах
```

Проблеми:
- **Низька когезія** — файли однієї фічі розкидані по різних папках/проєктах
- **Жорсткі правила** — Controller МУСИТЬ говорити з Service, Service МУСИТЬ використовувати Repository
- **Багато абстракцій** — інтерфейси заради інтерфейсів
- **Mock-heavy тести** — щоб протестувати логіку, треба мокати всю інфраструктуру

## Vertical Slice Architecture

```
Vertical Slices:

     [Feature A]  [Feature B]  [Feature C]
     ┌─────────┐  ┌─────────┐  ┌─────────┐
     │ endpoint│  │ endpoint│  │ endpoint│
     │ handler │  │ handler │  │ handler │
     │ model   │  │ query   │  │ model   │
     │ repo    │  │         │  │ events  │
     └─────────┘  └─────────┘  └─────────┘
         │              │            │
     coupling:    coupling:    coupling:
     вертикальний  вертикальний  вертикальний

  Між slices: мінімальний coupling
  Всередині slice: максимальний coupling
```

> *"Minimize coupling between slices, and maximize coupling in a slice."* — Jimmy Bogard

## Ключові принципи

### 1. Кожен request — окремий use case

Система природно розбивається на **commands** (POST/PUT/DELETE) та **queries** (GET) → CQRS з коробки.

### 2. Кожен slice обирає свій підхід

Не потрібно один Domain Logic pattern на весь застосунок:

```
PlaceOrder slice    → Impureim Sandwich + sealed class рішення
GetOrderById slice  → прямий SQL-запит
CancelOrder slice   → Domain model з бізнес-правилами
ListOrders slice    → projection без бізнес-логіки
```

### 3. Нові фічі = новий код, не зміна існуючого

Додавання фічі — створення нового slice. Існуючий код не змінюється, немає побічних ефектів.

## Структура проєкту (Kotlin)

### Layered (до):

```
src/main/kotlin/com/example/
├── controllers/
│   ├── OrderController.kt
│   ├── CustomerController.kt
│   └── ProductController.kt
├── services/
│   ├── OrderService.kt
│   ├── CustomerService.kt
│   └── ProductService.kt
├── repositories/
│   ├── OrderRepository.kt
│   ├── CustomerRepository.kt
│   └── ProductRepository.kt
└── models/
    ├── Order.kt
    ├── Customer.kt
    └── Product.kt
```

### Vertical Slices (після):

```
src/main/kotlin/com/example/
├── orders/
│   ├── placeOrder/
│   │   ├── PlaceOrder.kt          ← handler (sandwich)
│   │   ├── PlaceOrderRequest.kt   ← input DTO
│   │   └── OrderDecision.kt       ← sealed class рішення
│   ├── getOrder/
│   │   ├── GetOrder.kt            ← handler (прямий запит)
│   │   └── OrderResponse.kt       ← output DTO
│   └── cancelOrder/
│       ├── CancelOrder.kt
│       └── CancelDecision.kt
├── customers/
│   └── ...
└── common/                        ← мінімум shared коду
    ├── db/
    └── http/
```

## Приклад: Vertical Slice як Impureim Sandwich (Kotlin)

Кожен slice — це природний **Recawr Sandwich** (Read → Calculate → Write):

```kotlin
// orders/placeOrder/PlaceOrder.kt — один файл, один slice

// --- Request / Response ---
data class PlaceOrderRequest(
    val customerId: CustomerId,
    val items: List<OrderItemDto>,
    val requestedDelivery: String
)

// --- Decision (sealed class) ---
sealed class OrderDecision {
    data class Fulfillable(val order: Order, val reservations: List<Reservation>) : OrderDecision()
    data class OutOfStock(val missing: List<ProductId>) : OrderDecision()
    data class InvalidRequest(val reason: String) : OrderDecision()
}

// 🟢 Pure: вся бізнес-логіка — тестується без mock'ів
fun buildOrder(
    items: List<OrderItem>,
    stock: Map<ProductId, Int>,
    prices: Map<ProductId, BigDecimal>
): OrderDecision {
    val missing = items.filter { (stock[it.productId] ?: 0) < it.quantity }
    if (missing.isNotEmpty()) return OutOfStock(missing.map { it.productId })

    val total = items.sumOf { prices[it.productId]!! * it.quantity.toBigDecimal() }
    return Fulfillable(
        order = Order(items = items, total = total),
        reservations = items.map { Reservation(it.productId, it.quantity) }
    )
}

// 🔴 Impure edge: Recawr Sandwich
fun PlaceOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository,
    pricingRepo: PricingRepository
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 pure: parse & validate
    val items = request.items.map { parseOrderItem(it) ?: return@PlaceOrder HttpResult.BadRequest("Invalid item") }

    // 🔴 impure: READ
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val prices = pricingRepo.getCurrentPrices(items.map { it.productId })

    // 🟢 pure: CALCULATE
    val decision = buildOrder(items, stock, prices)

    // 🔴 impure: WRITE
    when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            inventoryRepo.reserve(decision.reservations)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict("Out of stock: ${decision.missing}")
        is InvalidRequest -> HttpResult.BadRequest(decision.reason)
    }
}
```

### Інший slice — зовсім інший підхід

```kotlin
// orders/getOrder/GetOrder.kt — простий query, без бізнес-логіки

data class OrderResponse(val id: String, val status: String, val total: BigDecimal)

fun GetOrder(db: Database): suspend (OrderId) -> HttpResult = { orderId ->
    // Простий запит — не потрібен sandwich, sealed class чи domain model
    val row = db.query("SELECT id, status, total FROM orders WHERE id = ?", orderId)
    if (row != null)
        HttpResult.Ok(OrderResponse(row.id, row.status, row.total))
    else
        HttpResult.NotFound("Order $orderId not found")
}
```

## VSA + Impureim Sandwich

Vertical Slice Architecture і Impureim Sandwich — **ідеальна пара**:

```
┌──────────────────────────────────────────────────────┐
│  Vertical Slice Architecture                         │
│  ────────────────────────────                        │
│  "ЯК організувати код" — по фічах, не по шарах      │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  Impureim Sandwich                             │  │
│  │  ─────────────────                             │  │
│  │  "ЯК структурувати кожну фічу"                │  │
│  │  impure (read) → pure (decide) → impure (write)│  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

- **VSA** відповідає на питання: де живе код?
- **Impureim Sandwich** відповідає на питання: як структурована логіка всередині?

## Коли НЕ працює

- Команда **не вміє** розпізнавати code smells і рефакторити
- Slice росте — потрібно вчасно виносити domain logic
- Shared logic між slices — потрібна дисципліна, щоб `common/` не став новим "service layer"

> *"If your team does not understand when a 'service' is doing too much to push logic to the domain, this pattern is likely not for you."* — Jimmy Bogard

## Ключові цитати

> *"Instead of coupling across a layer, we couple vertically along a slice."*

> *"New features only add code, you're not changing shared code and worrying about side effects."*

> *"We can start simple (Transaction Script) and simply refactor to the patterns that emerge from code smells we see in the business logic."*

## Зв'язок з іншими статтями

- → [Impureim Sandwich](02-impureim-sandwich.md) — як структурувати кожен slice
- → [Recawr Sandwich](05-recawr-sandwich.md) — більшість slices = Read → Calculate → Write
- → [Decoupling Decisions from Effects](03-decoupling-decisions-from-effects.md) — sealed class рішення всередині slice
- → [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — curried slice замість class з DI
- ← [Functional Architecture](01-functional-architecture.md) — теоретична основа
