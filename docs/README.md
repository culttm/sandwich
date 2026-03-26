# Impureim Sandwich — Збірка саммарі

Колекція саммарі статей [Mark Seemann (ploeh)](https://blog.ploeh.dk) про паттерн **Impureim Sandwich** та пов'язані концепції функціональної архітектури.

Всі приклади коду — **Kotlin**, побудовані навколо вигаданого **awesome-flow-service** (сервіс обробки замовлень: валідація, інвентаризація, знижки, оплата, доставка).

## Порядок читання

| # | Файл | Стаття | Рівень |
|---|------|--------|--------|
| 1 | [01-functional-architecture.md](01-functional-architecture.md) | Functional architecture: a definition | 🟢 Основа — визначення |
| 2 | [02-impureim-sandwich.md](02-impureim-sandwich.md) | Impureim sandwich | 🟢 Основа — сам паттерн |
| 3 | [03-decoupling-decisions-from-effects.md](03-decoupling-decisions-from-effects.md) | Decoupling decisions from effects | 🟡 Практика — як розділяти |
| 4 | [04-whats-a-sandwich.md](04-whats-a-sandwich.md) | What's a sandwich? | 🟡 Еволюція — межі паттерну |
| 5 | [05-recawr-sandwich.md](05-recawr-sandwich.md) | Recawr Sandwich | 🟡 Спеціалізація — Read/Calculate/Write |
| 6 | [06-from-di-to-dependency-rejection.md](06-from-di-to-dependency-rejection.md) | From DI to dependency rejection | 🔵 Контекст — від OOP до FP |
| 7 | [07-ports-and-adapters.md](07-ports-and-adapters.md) | Functional architecture is Ports and Adapters | 🔵 Контекст — зв'язок з Hexagonal |
| 8 | [08-discerning-purity.md](08-discerning-purity.md) | Discerning and maintaining purity | 🔴 Практика — як не зламати чистоту |
| 9 | [09-asynchronous-injection.md](09-asynchronous-injection.md) | Asynchronous Injection | 🔴 Практика — suspend у сендвічі |

## Візуальна модель

```
Ідеальний сендвіч:          Реальний сендвіч:

🔴 Impure (read)            🟢 Pure  (validation/parsing)
🟢 Pure   (logic)           🔴 Impure (read from DB)
🔴 Impure (write)           🟢 Pure  (business logic)
                            🔴 Impure (write to DB)
                            🟢 Pure  (translate response)
```

## Ключове правило

> **Functional Interaction Law**: чиста функція НЕ МОЖЕ викликати impure дію.
> Impure дія МОЖЕ викликати чисту функцію.

|  | Callee: Impure | Callee: Pure |
|---|---|---|
| **Caller: Impure** | ✅ Valid | ✅ Valid |
| **Caller: Pure** | ❌ Invalid | ✅ Valid |

## Kotlin-специфічні маркери impurity

| Impure ознака | Pure альтернатива |
|---|---|
| `suspend fun` | `fun` (non-suspend) |
| `LocalDateTime.now()` | Передати `now: LocalDateTime` як аргумент |
| `transaction(db) { }` | Працювати з даними, отриманими на edge |
| `repository.read(...)` | Передати результат як аргумент |
| `logger.info(...)` | Логувати тільки на edge |
| `UUID.randomUUID()` | Генерувати на edge, передати як аргумент |

## Джерело

Всі статті: [blog.ploeh.dk](https://blog.ploeh.dk) © Mark Seemann
