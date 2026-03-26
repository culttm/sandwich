# Asynchronous Injection

> **Оригінал**: [blog.ploeh.dk/2019/02/11/asynchronous-injection](https://blog.ploeh.dk/2019/02/11/asynchronous-injection/)
> **Дата**: 11 лютого 2019
> **Автор**: Mark Seemann

---

## Головна ідея

`async/await` (у C#) та `suspend` (у Kotlin) — це **leaky abstraction**, яка інфікує весь код. Функціональний підхід (Impureim Sandwich) дозволяє утримати `suspend` на edge і залишити бізнес-логіку звичайною (non-suspend) та чистою.

## Проблема: suspend заражає все

### Крок 1: синхронний Repository

```kotlin
interface InventoryRepository {
    fun checkStock(productIds: List<ProductId>): Map<ProductId, Int>
}

interface OrderRepository {
    fun create(order: Order): OrderId
}
```

### Крок 2: додаємо suspend (бо DB — це I/O)

```kotlin
interface InventoryRepository {
    suspend fun checkStock(productIds: List<ProductId>): Map<ProductId, Int>
}

interface OrderRepository {
    suspend fun create(order: Order): OrderId
}
```

**Перша leaky abstraction**: інтерфейс належить клієнту (OrderService), а не реалізації. OrderService не потребує suspend — це деталь реалізації PostgreSQL-адаптера.

### Крок 3: suspend поширюється вгору

```kotlin
// OrderService тепер теж suspend
class OrderService(
    private val inventoryRepo: InventoryRepository,
    private val orderRepo: OrderRepository
) {
    suspend fun placeOrder(request: PlaceOrderRequest): OrderId? {
        val stock = inventoryRepo.checkStock(request.productIds())  // suspend
        // ... бізнес-логіка ...
        return orderRepo.create(order)                               // suspend
    }
}

// Route handler теж suspend
suspend fun handlePost(request: PlaceOrderRequest): HttpResult { ... }

// Kafka consumer теж suspend
// Cron job теж suspend
// ... suspend all the way up!
```

**Друга leaky abstraction** (гірша): бізнес-логіка стала suspend через деталь інфраструктури.

У Kotlin це менш болюче ніж у C# (coroutines інтегровані в мову), але проблема та сама: **деталь реалізації пронизує всю архітектуру**.

## Рішення: Impureim Sandwich

Замість інжекту suspend залежностей — **винести IO на edge**:

### До: OrderService з DI (impure)

```kotlin
class OrderService(
    private val inventoryRepo: InventoryRepository,
    private val orderRepo: OrderRepository
) {
    suspend fun placeOrder(request: PlaceOrderRequest): OrderResult {
        // IO всередині бізнес-логіки
        val stock = inventoryRepo.checkStock(request.productIds())
        val outOfStock = request.items.filter { (stock[it.productId] ?: 0) < it.quantity }

        if (outOfStock.isNotEmpty())
            return OrderResult.OutOfStock(outOfStock)

        val order = Order(request.items, calculateTotal(request))
        val id = orderRepo.create(order)
        return OrderResult.Created(id)
    }
}
```

`placeOrder` — suspend, impure, важко тестувати (потрібен mock repository).

### Після: чиста бізнес-логіка без suspend

```kotlin
// 🟢 Pure: НЕ suspend, НЕ потребує repository
// Приймає дані, повертає рішення
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

### Композиція на edge (Route handler)

```kotlin
// 🔴 Impure: suspend живе ТІЛЬКИ тут
suspend fun placeOrder(request: PlaceOrderRequest): HttpResult {
    // 🔴 impure: read (suspend)
    val stock = inventoryRepo.checkStock(request.productIds())

    // 🟢 pure: decide (NOT suspend — звичайна функція)
    val decision = buildOrder(stock, request)

    // 🔴 impure: write (suspend)
    return when (decision) {
        is Fulfillable -> {
            val id = orderRepo.create(decision.order)
            HttpResult.Created(id)
        }
        is OutOfStock -> HttpResult.Conflict(decision.missing)
    }
}
```

**suspend залишається тільки на edge.** Бізнес-логіка — звичайна (non-suspend), чиста, тестується без coroutines.

## Тестування: suspend vs. non-suspend

### Тест для impure версії (потрібен mock + runTest)

```kotlin
@Test
fun `place order with enough stock`() = runTest {
    val inventoryRepo = mockk<InventoryRepository>()
    val orderRepo = mockk<OrderRepository>()
    coEvery { inventoryRepo.checkStock(any()) } returns mapOf(ProductId("A") to 10)
    coEvery { orderRepo.create(any()) } returns OrderId("ord-42")

    val service = OrderService(inventoryRepo, orderRepo)
    val result = service.placeOrder(request)

    assertEquals(OrderResult.Created(OrderId("ord-42")), result)
    coVerify { orderRepo.create(any()) }
}
```

### Тест для pure версії (просто виклик функції)

```kotlin
@Test
fun `build order with enough stock`() {
    // Не потрібен runTest, mock, coEvery — просто дані
    val stock = mapOf(ProductId("A") to 10, ProductId("B") to 5)
    val request = PlaceOrderRequest(
        items = listOf(
            OrderItem(ProductId("A"), quantity = 2),
            OrderItem(ProductId("B"), quantity = 1)
        )
    )

    val decision = buildOrder(stock, request)

    assertIs<Fulfillable>(decision)
    assertEquals(3, decision.order.items.sumOf { it.quantity })
}

@Test
fun `build order with insufficient stock`() {
    val stock = mapOf(ProductId("A") to 1)  // мало!
    val request = PlaceOrderRequest(
        items = listOf(OrderItem(ProductId("A"), quantity = 5))
    )

    val decision = buildOrder(stock, request)

    assertIs<OutOfStock>(decision)
    assertEquals(listOf(ProductId("A")), decision.missing)
}
```

Pure версія: **без mockk, без coEvery, без runTest**. Просто дані → функція → перевірка.

## Ширший приклад: awesome-flow-service — повний flow

```kotlin
// Edge: suspend, біндить інфраструктуру
fun ProcessOrder(
    inventoryRepo: InventoryRepository,
    orderRepo: OrderRepository,
    paymentService: PaymentService,
    emailService: EmailService
): suspend (PlaceOrderRequest) -> HttpResult = { request ->
    // 🟢 NOT suspend: parse & validate
    val validated = validateOrder(LocalDate.now(), request)
        ?: return@ProcessOrder HttpResult.BadRequest("Invalid order")

    // 🔴 suspend: read
    val stock = inventoryRepo.checkStock(validated.productIds())
    val prices = inventoryRepo.getCurrentPrices(validated.productIds())

    // 🟢 NOT suspend: pure business logic
    val decision = buildOrder(stock, prices, validated)

    // 🔴 suspend: write
    when (decision) {
        is Fulfillable -> {
            val orderId = orderRepo.create(decision.order)
            paymentService.charge(validated.paymentMethod, decision.order.total)
            emailService.sendConfirmation(validated.email, orderId)
            HttpResult.Created(orderId)
        }
        is OutOfStock -> HttpResult.Conflict(decision.missing)
        is PriceChanged -> HttpResult.Conflict(decision.newPrices)
    }
}
```

`buildOrder` — non-suspend, pure. Вся складна логіка (знижки, доставка, перевірка цін) — тестується без coroutines.

## Зв'язок з іншими статтями

- ← [Impureim Sandwich](02-impureim-sandwich.md) — паттерн, який застосовується
- ← [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — мотивація відмовитись від DI
- ← [Functional Architecture](01-functional-architecture.md) — чому pure функції не повинні бути suspend
- → [What's a sandwich?](04-whats-a-sandwich.md) — реалістичні обмеження композиції
