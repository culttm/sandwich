# Functional Architecture: a Definition

> **Оригінал**: [blog.ploeh.dk/2018/11/19/functional-architecture-a-definition](https://blog.ploeh.dk/2018/11/19/functional-architecture-a-definition/)
> **Дата**: 19 листопада 2018
> **Автор**: Mark Seemann

---

## Головна ідея

Стаття дає **формальне, фальсифіковане визначення** функціональної архітектури — на відміну від OOP, де ніхто не може точно сказати, що є "справжнім ООП".

## Referential Transparency — фундамент

Чиста функція (pure function) повинна мати дві властивості:

1. **Детермінізм** — однаковий вхід завжди дає однаковий вихід
2. **Відсутність побічних ефектів** — функція нічого не змінює в зовнішньому світі

Все інше в FP (іммутабельність, рекурсія, функтори, монади) — **наслідок** цих двох правил.

## Functional Interaction Law

> Чиста функція **не може** викликати impure дію.

Це єдине правило, яке визначає функціональну архітектуру:

```
              Callee
            Impure  Pure
Caller  Impure  ✅     ✅
        Pure    ❌     ✅
```

**Функціональна архітектура** = код, що дотримується цього закону + значна частина коду є чистою.

## Дві групи операцій

```
┌─────────────────┐     ┌─────────────────┐
│  Impure Actions │────▶│  Pure Functions  │
│  (IO, DB, HTTP) │     │  (бізнес-логіка)│
│                 │     │                 │
│  ↺ можуть       │     │  ↺ можуть       │
│  викликати      │     │  викликати      │
│  одне одного    │     │  одне одного    │
└─────────────────┘     └─────────────────┘
       │                        ▲
       │    можуть викликати    │
       └────────────────────────┘

  Pure ──╳──▶ Impure  (ЗАБОРОНЕНО)
```

## Проблема: як перевірити?

У більшості мов (Kotlin, C#, Java, F#) **немає інструментів** для автоматичної перевірки чистоти. Тільки **Haskell** (та PureScript, Idris) забезпечують це на рівні типів через `IO` монаду.

Kotlin не розрізняє pure та impure — це і перевага (простіше писати), і недолік (компілятор не захистить).

### Приклад помилки (Kotlin)

```kotlin
// Виглядає як pure функція...
fun buildInvoice(
    order: Order,
    discountRules: List<DiscountRule>,
    taxRate: BigDecimal
): Invoice {
    val subtotal = order.items.sumOf { it.price * it.quantity }
    val discount = applyDiscounts(subtotal, discountRules)
    val tax = (subtotal - discount) * taxRate

    return Invoice(
        orderId = order.id,
        subtotal = subtotal,
        discount = discount,
        tax = tax,
        total = subtotal - discount + tax,
        issuedAt = Instant.now()  // ← НЕ ЧИСТО! Instant.now() — non-deterministic
    )
}
```

`Instant.now()` робить **всю функцію** та **все, що її викликає**, impure. У Haskell це просто не скомпілюється.

## Ключові цитати

> *"Functional architecture is code that obeys the functional interaction law, and that is made up of a significant portion of pure functions."*

> *"Ultimately, functional architecture isn't a goal in itself. It's a means to achieve an objective, such as a sustainable code base."*

## Зв'язок з іншими статтями

- → [Impureim Sandwich](02-impureim-sandwich.md) — конкретний паттерн, що реалізує цю архітектуру
- → [Ports and Adapters](07-ports-and-adapters.md) — функціональна архітектура ≈ Hexagonal Architecture
- → [Discerning Purity](08-discerning-purity.md) — як на практиці підтримувати чистоту
