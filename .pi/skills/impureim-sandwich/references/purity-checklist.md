# Purity Checklist

How to identify, verify, and maintain purity in Kotlin code.

## Quick Scan: Impurity Markers

Scan every function intended to be pure for these markers:

| Marker | Why impure | Fix |
|--------|-----------|-----|
| `suspend fun` | Signals IO capability | Change to `fun`, move IO to caller |
| `Instant.now()` / `LocalDate.now()` / `LocalDateTime.now()` | Non-deterministic | Add `now` parameter |
| `System.currentTimeMillis()` | Non-deterministic | Add `now` parameter |
| `UUID.randomUUID()` | Non-deterministic | Generate on edge, pass as argument |
| `Random.nextInt()` / `Random.next*()` | Non-deterministic | Pass seed or generated value |
| `repository.*(...)` / any DB call | Side effect (IO) | Call on edge, pass result |
| `httpClient.*(...)` / any HTTP call | Side effect (IO) | Call on edge, pass result |
| `logger.info/warn/error(...)` | Side effect (output) | Log only on edge |
| `transaction(db) { }` | Side effect (DB) | Only on edge |
| `System.getenv(...)` | Non-deterministic (env-dependent) | Read at startup, pass as value |
| `File(...).readText()` / `File(...).exists()` | Side effect (IO) | Call on edge, pass result |
| `println(...)` | Side effect (output) | Remove or log on edge |
| `mutableListOf()` used across calls | Shared mutable state | Use immutable collections |

## Transitivity Rule

> If function `f` calls impure function `g`, then `f` is also impure.
> This propagates up the **entire call stack**.

```
g() calls Instant.now()     → g is impure
f() calls g()               → f is impure
handler() calls f()         → handler is impure
```

One `Instant.now()` deep in the code can destroy purity of the entire architecture.

## The Silent Breakage Pattern

A pure function can become impure **without changing its signature**:

```kotlin
// v1: PURE ✅
fun validateOrder(dto: PlaceOrderDto): String {
    if (dto.items.isEmpty()) return "Order must have items"
    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull()
        ?: return "Invalid date"
    return ""
}

// v2: IMPURE ❌ — signature unchanged!
fun validateOrder(dto: PlaceOrderDto): String {
    if (dto.items.isEmpty()) return "Order must have items"
    val date = runCatching { LocalDate.parse(dto.requestedDelivery) }.getOrNull()
        ?: return "Invalid date"
    if (date < LocalDate.now()) return "Date must be in the future"  // ← broke purity
    return ""
}
```

- Signature `fun validateOrder(dto: PlaceOrderDto): String` — same
- Tests keep passing (until the hardcoded date becomes past)
- Tests will fail **silently** months later

**Fix:** pass `now` as parameter, making the dependency explicit:

```kotlin
fun validateOrder(now: LocalDate, dto: PlaceOrderDto): String { ... }
```

## Suspend Containment

### Rule: `suspend` lives ONLY on the edge

```
Edge layer (suspend):     route handler, kafka consumer, cron job
  ↓ calls
Pure layer (NOT suspend): business logic, validation, decisions
  ↓ returns to
Edge layer (suspend):     write results, send responses
```

### Why suspend is a purity signal

In Kotlin, `suspend` is the closest thing to Haskell's `IO` marker:
- `suspend fun` = "this function MAY do IO" → treat as impure
- `fun` (non-suspend) = "this function CANNOT do IO" → likely pure (still verify)

### Leaky abstraction check

If a repository interface forces `suspend` on its consumers:

```kotlin
interface InventoryRepository {
    suspend fun checkStock(ids: List<ProductId>): Map<ProductId, Int>
}
```

Then anything injecting this repository becomes `suspend` → impure.

**Fix:** don't inject the repository into business logic. Call it on edge, pass data:

```kotlin
// Edge (suspend)
val stock = inventoryRepo.checkStock(ids)
// Pure (not suspend)
val decision = buildOrder(stock, request)
```

## Review Process

When reviewing a function claimed to be pure:

1. **Check signature** — is it `suspend`? If yes → impure
2. **Scan body** — any markers from the table above?
3. **Check callees** — does it call other functions? Are THEY pure?
4. **Check parameters** — does it receive function types? Are they `suspend`?
5. **Check for hidden state** — does it access companion object, global, or mutable state?

## Testing Purity

Pure functions should be testable with this pattern:

```kotlin
@Test
fun `test name`() {
    // Arrange — plain data, no mocks
    val input = SomeData(...)

    // Act — direct function call, no runTest, no coroutines
    val result = pureFunction(input)

    // Assert — check returned value
    assertEquals(expected, result)
}
```

If you need **any** of these, the function is NOT pure:
- `runTest { }` or `runBlocking { }`
- `mockk<SomeRepository>()`
- `coEvery { ... }`
- `coVerify { ... }`
- Test setup with real/fake DB
- `@BeforeEach` that starts infrastructure

## Ambient Impurity (Easy to Miss)

| Looks pure but isn't | Why |
|---------------------|-----|
| `data class` with `init { require(...) }` | Pure if deterministic, but `require` throws → check callers handle it |
| `toString()` override | Usually pure, unless it logs or accesses state |
| `equals()` with lazy fields | If lazy triggers IO → impure |
| `Companion.create()` factory | Check for `Instant.now()`, `UUID.randomUUID()` inside |
| Extension functions | Check receiver — if `suspend` receiver, the extension is too |
