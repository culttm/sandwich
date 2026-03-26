# Discerning and Maintaining Purity

> **Оригінал**: [blog.ploeh.dk/2020/02/24/discerning-and-maintaining-purity](https://blog.ploeh.dk/2020/02/24/discerning-and-maintaining-purity/)
> **Дата**: 24 лютого 2020
> **Автор**: Mark Seemann

---

## Головна ідея

Ідентифікувати і **підтримувати** чистоту функцій у mainstream мовах (Kotlin, C#, Java) — **надзвичайно складно**. Мова не допомагає, тому потрібна постійна пильність.

## Проблема: мова не розрізняє pure і impure

```kotlin
val result = OrderValidator.validate(dto)
```

Чи це pure функція? **Неможливо визначити за сигнатурою.** Треба читати код — а це руйнує абстракцію та інкапсуляцію.

> *"When you have to read the code of a method, it indicates a lack of abstraction and encapsulation."*

## Ручний аналіз: крихкий і тимчасовий

### Оригінальна версія — PURE ✅

```kotlin
fun validateOrder(dto: PlaceOrderDto): String {
    if (dto.items.isEmpty()) return "Order must have at least one item"
    if (dto.items.any { it.quantity <= 0 }) return "Quantity must be positive"
    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull()
    if (date == null) return "Invalid delivery date: ${dto.requestedDelivery}"
    return ""
}
```

Детермінована, без side effects → **чиста**.

### Після змін — IMPURE ❌

```kotlin
fun validateOrder(dto: PlaceOrderDto): String {
    if (dto.items.isEmpty()) return "Order must have at least one item"
    if (dto.items.any { it.quantity <= 0 }) return "Quantity must be positive"

    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull()
        ?: return "Invalid delivery date: ${dto.requestedDelivery}"

    if (date < LocalDate.now())        // ← LocalDate.now() = non-deterministic!
        return "Delivery date must be in the future"

    return ""
}
```

Одна строчка (`LocalDate.now()`) зробила **всю функцію** та **все, що її викликає**, impure. Причому:

- Сигнатура `fun validateOrder(dto: PlaceOrderDto): String` **не змінилась**
- Тести продовжують проходити (поки дата в тесті в майбутньому)
- Тест зламається **мовчки** — через рік, коли `requestedDelivery = "2025-06-15"` стане минулою

## Чому naming conventions не працюють

```kotlin
// Назва "validate" натякає на pure, але компілятор не перевіряє
fun validateOrder(dto: PlaceOrderDto): String {
    // ... LocalDate.now() всередині ...
}
```

Kotlin не має `@Pure` анотації з верифікацією, а навіть якби мав — він не зміг би перевірити транзитивну чистоту.

## Транзитивність impurity

> Impurity **транзитивна**: якщо `f` викликає impure `g`, то `f` — теж impure. Це поширюється на **весь call stack**.

```kotlin
fun validateOrder(dto: PlaceOrderDto): String {
    // ...
    if (date < LocalDate.now()) ...  // ← impure
    // ...
}

// Все, що викликає validateOrder — тепер теж impure:
fun processOrder(dto: PlaceOrderDto): OrderResult {
    val error = validateOrder(dto)  // ← transitively impure!
    // ...
}
```

Один `LocalDate.now()` глибоко в коді може зруйнувати чистоту всієї архітектури.

## Правильне рішення: передай час як аргумент

```kotlin
// 🟢 Pure: now — просто значення, не системний виклик
fun validateOrder(now: LocalDate, dto: PlaceOrderDto): String {
    if (dto.items.isEmpty()) return "Order must have at least one item"
    if (dto.items.any { it.quantity <= 0 }) return "Quantity must be positive"

    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull()
        ?: return "Invalid delivery date: ${dto.requestedDelivery}"

    if (date < now)
        return "Delivery date must be in the future"

    return ""
}
```

IO (отримання поточного часу) відбувається **на edge**:

```kotlin
// 🔴 Impure: edge of the system
suspend fun placeOrder(dto: PlaceOrderDto): HttpResult {
    val now = LocalDate.now()                                // 🔴 impure: отримати час
    val error = validateOrder(now, dto)                      // 🟢 pure: валідація
    if (error.isNotEmpty()) return HttpResult.BadRequest(error)

    val stock = inventoryRepo.checkStock(dto.productIds())   // 🔴 impure: read
    val decision = buildOrder(stock, dto)                     // 🟢 pure: logic
    // ...
}
```

Тепер `validateOrder` легко тестувати з **будь-якою** датою:

```kotlin
@Test
fun `valid order with future delivery`() {
    val now = LocalDate.of(2020, 1, 1)
    val dto = PlaceOrderDto(
        items = listOf(OrderItemDto("SKU-1", 2)),
        requestedDelivery = "2025-12-21"
    )
    assertEquals("", validateOrder(now, dto))
}

@Test
fun `delivery date in the past`() {
    val now = LocalDate.of(2025, 6, 1)
    val dto = PlaceOrderDto(
        items = listOf(OrderItemDto("SKU-1", 2)),
        requestedDelivery = "2024-01-15"
    )
    assertEquals("Delivery date must be in the future", validateOrder(now, dto))
}

// Стабільні назавжди — "now" контролюється тестом
```

## Чеклист: як не зламати чистоту в Kotlin

| Порушник | Як виправити |
|----------|-------------|
| `LocalDate.now()`, `Instant.now()` | Передати як аргумент |
| `System.currentTimeMillis()` | Передати як аргумент |
| `UUID.randomUUID()` | Передати як аргумент або генерувати на edge |
| `Random.nextInt()` | Передати seed або результат як аргумент |
| `logger.info(...)` | Тільки на edge; pure функції не логують |
| `transaction(db) { ... }` | Тільки на edge; pure функції працюють з даними |
| `httpClient.get(...)` | Тільки на edge; передати результат як аргумент |
| `System.getenv("KEY")` | Читати конфіг на старті, передавати як значення |

## Зв'язок з іншими статтями

- ← [Functional Architecture](01-functional-architecture.md) — визначення чистоти
- ← [Impureim Sandwich](02-impureim-sandwich.md) — куди поставити impure код
- → [What's a sandwich?](04-whats-a-sandwich.md) — валідація як перший чистий шар
