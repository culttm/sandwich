# What's a Sandwich?

> **Оригінал**: [blog.ploeh.dk/2023/10/09/whats-a-sandwich](https://blog.ploeh.dk/2023/10/09/whats-a-sandwich/)
> **Дата**: 9 жовтня 2023
> **Автор**: Mark Seemann

---

## Головна ідея

Ідеальний Impureim Sandwich (impure → pure → impure) на практиці рідко існує в чистому вигляді. Реальність — це **5-шаровий сендвіч**: pure → impure → pure → impure → pure. І це нормально.

## Ідеал vs. реальність

### Ідеальний сендвіч

```
🔴 Impure  — зібрати дані
🟢 Pure    — бізнес-логіка
🔴 Impure  — записати результат
```

### Реальний сендвіч

```
🟢 Pure    — валідація / парсинг входу
🔴 Impure  — читання з БД
🟢 Pure    — бізнес-логіка
🔴 Impure  — запис у БД
🟢 Pure    — трансляція результату у відповідь
```

## Чому перший чистий шар неминучий?

### Парсинг перед IO

Щоб зробити запит до БД, потрібно знати, що шукати. Дані приходять як JSON. Парсинг — **чиста функція**. Якщо JSON битий, ви навіть не знаєте, що питати у БД.

> *"The value might be anything. If it's sufficiently malformed, you can't even perform the impure action of querying the database, because you don't know what to query it about."*

Це принцип **[Parse, don't validate](https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/)**: валідація — це парсинг, а парсинг — чиста функція.

### Приклад (Kotlin) — awesome-flow-service

```kotlin
suspend fun placeOrder(dto: PlaceOrderDto): HttpResult {
    // 🟢 pure: парсинг і валідація — ще до будь-якого IO
    val shippingDate = parseDate(dto.requestedDelivery)
        ?: return HttpResult.BadRequest("Invalid date: ${dto.requestedDelivery}")
    val items = dto.items.map { parseOrderItem(it) ?: return HttpResult.BadRequest("Invalid item") }
    if (items.isEmpty()) return HttpResult.BadRequest("Order must have items")

    // 🔴 impure: тепер знаємо ЩО шукати в БД
    val stock = inventoryRepo.checkStock(items.map { it.productId })
    val pricing = pricingRepo.getCurrentPrices(items.map { it.productId })

    // 🟢 pure: бізнес-рішення
    val decision = buildOrder(items, stock, pricing, shippingDate)

    // 🔴 impure: запис
    return when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            HttpResult.Created(id)                     // 🟢 pure: трансляція
        }
        is OutOfStock ->
            HttpResult.Conflict(decision.message)      // 🟢 pure: трансляція
    }
}

// 🟢 Pure functions
fun parseDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw) }.getOrNull()

fun parseOrderItem(dto: OrderItemDto): OrderItem? {
    if (dto.quantity <= 0) return null
    if (dto.productId.isBlank()) return null
    return OrderItem(ProductId(dto.productId), dto.quantity)
}
```

Структура:

```
🟢 parseDate(), parseOrderItem()            — pure (парсинг)
🔴 inventoryRepo.checkStock()               — impure (read)
   pricingRepo.getCurrentPrices()
🟢 buildOrder(items, stock, pricing, date)  — pure (логіка)
🔴 orderRepo.create(...)                    — impure (write)
🟢 HttpResult.Created() / .Conflict()       — pure (трансляція)
```

## Чому останній чистий шар неминучий?

Побудова HTTP-відповіді — чиста функція. Навіщо робити її impure?

```kotlin
// 🟢 pure: просте перетворення значення в response
fun created(id: OrderId) = HttpResult.Created(id)
fun conflict(msg: String) = HttpResult.Conflict(msg)
```

## Кінцеве визначення

> **Impureim sandwich може мати:**
> - **Максимум 2 impure фази** (read + write)
> - **Від 1 до 3 pure шарів** (валідація, логіка, трансляція)

### Що МОЖНА додавати

✅ Додаткові **чисті** шари — безпечно, збільшує тестованість.

### Що НЕ МОЖНА додавати

❌ Додаткові **impure** шари — це [Dagwood sandwich](https://en.wikipedia.org/wiki/Dagwood_sandwich), ознака поганого дизайну.

## Метафора

Марк порівнює з данськими **smørrebrød** (відкриті бутерброди): один шматок хліба з начинкою зверху — теж сендвіч, якщо його можна їсти руками. Аналогічно, pure шар зверху чи знизу impure — все ще сендвіч.

## Зв'язок з іншими статтями

- ← [Impureim Sandwich](02-impureim-sandwich.md) — ідеальна версія
- → [Recawr Sandwich](05-recawr-sandwich.md) — спеціалізація з ролями для impure шарів
- ← [Functional Architecture](01-functional-architecture.md) — теоретична основа
