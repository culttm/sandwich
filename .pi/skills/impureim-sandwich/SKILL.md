---
name: impureim-sandwich
description: >
  Functional architecture with Impureim/Recawr Sandwich pattern for Kotlin.
  Use when writing, reviewing, or refactoring Kotlin backend code —
  structuring pure/impure separation, modeling decisions with sealed classes,
  replacing DI with dependency rejection, or handling suspend on the edge.
---

# Impureim Sandwich

Structure Kotlin backend code as pure core + impure shell.

## The One Rule

> **Functional Interaction Law:** a pure function CANNOT call an impure action.

|  | Callee: Impure | Callee: Pure |
|---|---|---|
| **Caller: Impure** | ✅ | ✅ |
| **Caller: Pure** | ❌ | ✅ |

## Kotlin Impurity Markers

| Impure sign | Pure alternative |
|---|---|
| `suspend fun` | `fun` (non-suspend) |
| `LocalDateTime.now()` / `Instant.now()` | Pass `now` as argument |
| `UUID.randomUUID()` | Generate on edge, pass as argument |
| `Random.nextInt()` | Pass seed or result as argument |
| `repository.read(...)` / `repository.write(...)` | Pass result as argument |
| `httpClient.get(...)` | Call on edge, pass result as argument |
| `logger.info(...)` | Log only on edge |
| `transaction(db) { }` | Only on edge |
| `System.getenv("KEY")` | Read config at startup, pass as value |

Impurity is **transitive**: if `f` calls impure `g`, then `f` is impure too — up the entire call stack.

## Canonical Sandwich Structure (5-layer)

```kotlin
suspend fun handleRequest(dto: RequestDto): HttpResult {
    // 🟢 PURE — parse & validate (before any IO)
    val validated = validate(dto)
        ?: return HttpResult.BadRequest("Invalid input")

    // 🔴 IMPURE — read (suspend)
    val data = repository.fetch(validated.id)

    // 🟢 PURE — business decision (NOT suspend, no IO)
    val decision = decide(data, validated)

    // 🔴 IMPURE — write (suspend)
    return when (decision) {
        is Accept -> {
            repository.save(decision.result)
            HttpResult.Created(decision.result.id)  // 🟢 pure: translate
        }
        is Reject -> HttpResult.Conflict(decision.reason)  // 🟢 pure: translate
    }
}
```

Rules:
- **Max 2 impure phases** (read + write)
- **1–3 pure layers** (validate, decide, translate)
- Adding more pure layers → safe
- Adding more impure layers → Dagwood sandwich, redesign needed

## Decision Tree

### Writing new code
→ Use Recawr template: Read → Calculate → Write
→ See [patterns.md](references/patterns.md)

### Refactoring existing code
→ Extract pure logic from class with DI → dependency rejection
→ See [refactoring.md](references/refactoring.md)

### Reviewing code for purity
→ Scan for impurity markers, check transitivity, verify suspend containment
→ See [purity-checklist.md](references/purity-checklist.md)

### Need a concrete example
→ See [examples.md](references/examples.md)

## Anti-Patterns

### ❌ Curried impure ≠ pure
```kotlin
// WRONG: looks FP but is impure — checkStock goes to DB inside
fun BuildOrder(
    checkStock: suspend (List<ProductId>) -> Map<ProductId, Int>
): suspend (Request) -> Decision = { request ->
    val stock = checkStock(request.productIds())  // impure inside!
    // ...
}
```

### ✅ Pass values, not functions
```kotlin
// RIGHT: accepts data, returns decision
fun buildOrder(
    stock: Map<ProductId, Int>,
    request: Request
): Decision { /* pure */ }
```

### ❌ Write before Calculate
```kotlin
// WRONG: writing results before calculating the full picture
items.map { repo.update(it) }  // write first!
val result = summarize(results) // calculate after — too late
```

### ✅ Calculate before Write
```kotlin
val (toUpdate, invalid) = partition(items, existing) // calculate first
toUpdate.forEach { repo.update(it) }                 // write after
```

## Key Principle

> **Dependency Rejection**: instead of injecting a function that fetches data, fetch the data on the edge and pass the result to a pure function.

```
OOP:  bind Repository → call inside logic → impure
FP:   call Repository on edge → pass data to pure logic → testable
```

## Quality Check

Before finishing any implementation, verify:
1. All business logic functions are `fun` (not `suspend fun`)
2. No impurity markers inside pure functions
3. `suspend` only appears on the outermost edge layer
4. Decisions modeled as sealed class/interface
5. Pure functions testable with plain data (no mocks, no runTest)
