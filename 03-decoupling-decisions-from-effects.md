# Decoupling Decisions from Effects

> **Оригінал**: [blog.ploeh.dk/2016/09/26/decoupling-decisions-from-effects](https://blog.ploeh.dk/2016/09/26/decoupling-decisions-from-effects/)
> **Дата**: 26 вересня 2016
> **Автор**: Mark Seemann

---

## Головна ідея

Рішення (decisions) можна відокремити від побічних ефектів (effects) за допомогою **sealed class / sealed interface** (алгебраїчних типів). Рішення — чисті функції, ефекти — humble-функції, які не потрібно тестувати.

## Принцип

```
Логіка (рішення)  →  чисті функції  →  покриті тестами
Ефекти (IO)       →  humble функції →  тривіальні, без тестів
```

> Якщо impure функція має cyclomatic complexity = 1, її можна вважати **humble** і не тестувати.

## Задача-приклад

```kotlin
fun loadUserConfig(path: String): UserConfig {
    if (!File(path).exists()) return UserConfig.default()  // impure
    val json = File(path).readText()                       // impure
    return UserConfig.parse(json)                          // pure
}
```

Проблема: рішення (чи існує файл?) переплетено з ефектами (читання файлу). Як розділити?

## Три рішення

### 1. Sealed class — явне моделювання рішення

```kotlin
// Алгебраїчний тип: два варіанти рішення
sealed class ConfigAction {
    data class LoadFromFile(val path: String) : ConfigAction()
    data class UseDefaults(val config: UserConfig) : ConfigAction()
}

// 🟢 Pure: приймає рішення
fun decideConfigSource(path: String, fileExists: Boolean): ConfigAction =
    if (fileExists) ConfigAction.LoadFromFile(path)
    else ConfigAction.UseDefaults(UserConfig.default())

// 🔴 + 🟢 Humble: виконує ефект за рішенням
fun executeConfigAction(action: ConfigAction): UserConfig =
    when (action) {
        is ConfigAction.LoadFromFile -> UserConfig.parse(File(action.path).readText())
        is ConfigAction.UseDefaults -> action.config
    }

// Композиція (edge):
fun loadUserConfig(path: String): UserConfig {
    val exists = File(path).exists()              // 🔴 impure
    val action = decideConfigSource(path, exists)  // 🟢 pure
    return executeConfigAction(action)             // 🔴 humble
}
```

Тести для `decideConfigSource` — тривіальні:

```kotlin
@Test
fun `loads from file when it exists`() {
    val actual = decideConfigSource("/etc/app.json", fileExists = true)
    assertEquals(ConfigAction.LoadFromFile("/etc/app.json"), actual)
}

@Test
fun `uses defaults when file missing`() {
    val actual = decideConfigSource("/etc/app.json", fileExists = false)
    assertEquals(ConfigAction.UseDefaults(UserConfig.default()), actual)
}
```

### 2. Result — загальний інструмент для двох альтернатив

```kotlin
fun loadUserConfig(path: String): UserConfig {
    val result = if (File(path).exists())               // 🔴 impure
        Result.success(path)
    else
        Result.failure(FileNotFoundException(path))

    return result.fold(                                  // 🟢 pure розгалуження
        onSuccess = { UserConfig.parse(File(it).readText()) },  // 🔴 + 🟢
        onFailure = { UserConfig.default() }                     // 🟢 pure
    )
}
```

### 3. Nullable / Option — коли значення або є, або ні

```kotlin
fun loadUserConfig(path: String): UserConfig {
    return path
        .takeIf { File(it).exists() }             // 🔴 impure: filter
        ?.let { File(it).readText() }              // 🔴 impure: read
        ?.let { UserConfig.parse(it) }             // 🟢 pure: parse
        ?: UserConfig.default()                    // 🟢 pure: default
}
```

Kotlin-ідіоматичне рішення з `?.let` і `?:` — найкоротше.

## Ключовий інсайт

У всіх трьох прикладах рішення закодовані через **алгебраїчні типи**:
- `sealed class ConfigAction` — кастомний ADT
- `Result<T>` — Either-подібний тип
- `T?` (nullable) — Maybe/Option

**Impure функції** (`File.exists()`, `File.readText()`) залишаються humble — тривіальними обгортками без логіки.

## Реальніший приклад: awesome-flow-service

```kotlin
// Sealed class моделює рішення про знижку
sealed class DiscountDecision {
    data class ApplyDiscount(val rate: BigDecimal, val reason: String) : DiscountDecision()
    data object NoDiscount : DiscountDecision()
}

// 🟢 Pure: вся логіка знижок — чиста, легко тестувати
fun decideDiscount(
    order: Order,
    customerHistory: CustomerHistory
): DiscountDecision {
    if (customerHistory.totalOrders >= 50)
        return DiscountDecision.ApplyDiscount("0.15".toBigDecimal(), "loyalty_gold")
    if (customerHistory.totalOrders >= 10)
        return DiscountDecision.ApplyDiscount("0.05".toBigDecimal(), "loyalty_silver")
    if (order.total > "500.00".toBigDecimal())
        return DiscountDecision.ApplyDiscount("0.10".toBigDecimal(), "large_order")
    return DiscountDecision.NoDiscount
}

// 🔴 Impure edge: збирає дані, передає в pure, виконує ефект
suspend fun applyOrderDiscount(orderId: OrderId) {
    val order = orderRepo.findById(orderId)                              // 🔴 read
    val history = customerRepo.getHistory(order.customerId)              // 🔴 read
    val decision = decideDiscount(order, history)                        // 🟢 pure
    when (decision) {                                                    // 🔴 write
        is DiscountDecision.ApplyDiscount -> {
            orderRepo.updateTotal(orderId, order.total * (BigDecimal.ONE - decision.rate))
            auditLog.record(orderId, "discount_applied", decision.reason)
        }
        is DiscountDecision.NoDiscount -> {} // nothing to write
    }
}
```

## Правило

> Клади всю логіку в чисті функції. Реалізуй impure ефекти як humble-функції, які не потрібно тестувати.

## Зв'язок з іншими статтями

- ← [Functional Architecture](01-functional-architecture.md) — чому чистота важлива
- → [Impureim Sandwich](02-impureim-sandwich.md) — як це виглядає в цілому
- → [From DI to Dependency Rejection](06-from-di-to-dependency-rejection.md) — еволюція від DI
