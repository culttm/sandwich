# Optimistic Locking

Prevent concurrent modifications using a `version` field.

## How It Works

```
1. Read document (version = 3)
2. Pure logic → decide what to change
3. Write: findOneAndReplace WHERE _id = X AND version = 3 → set version = 4
4. If no match → someone else modified it → throw OptimisticLockException
```

No database-level locks. Safe for coroutines and high concurrency.

## Pattern: Save with Version

```kotlin
suspend fun MongoCollection<ProductBson>.saveProduct(
    product: Product,
    session: ClientSession,
    now: LocalDateTime = LocalDateTime.now()
): Product {
    val bson = product.toBson()

    return if (bson.version == 0) {
        // ── INSERT (new document) ──
        try {
            val doc = bson.copy(version = 1, createdAt = now, updatedAt = now)
            insertOne(session, doc)
            doc.toDomain()
        } catch (ex: MongoWriteException) {
            if (ex.error.category == ErrorCategory.DUPLICATE_KEY)
                throw AlreadyExistsException("Product ${product.id} already exists", ex)
            throw ex
        }
    } else {
        // ── UPDATE (existing document) ──
        try {
            val result = findOneAndReplace(
                session,
                filter = doc {
                    "_id" to product.id
                    "version" to product.version
                },
                replacement = bson.copy(
                    version = bson.version + 1,
                    updatedAt = now
                ),
                options = FindOneAndReplaceOptions()
                    .returnDocument(ReturnDocument.AFTER)
            )
            result?.toDomain()
                ?: throw OptimisticLockException(
                    "Product ${product.id} was modified (expected version ${product.version})"
                )
        } catch (ex: MongoCommandException) {
            if (ex.errorCodeName == "DuplicateKey")
                throw AlreadyExistsException("Product ${product.id} duplicate key on update", ex)
            throw ex
        }
    }
}
```

## Key Details

### Version = 0 means "new"

Convention: domain creates objects with `version = 0`. Save detects this and does `insertOne`. First persisted version is `1`.

```kotlin
data class Product(
    val id: String,
    val name: String,
    // ...
    val version: Int = 0   // 0 = not yet persisted
)
```

### `findOneAndReplace` — atomic read + write

The filter `{ _id: X, version: N }` ensures:
- If version matches → replace happens → returns new document
- If version doesn't match → returns `null` → throw exception

This is **atomic** at MongoDB level — no race condition.

### Duplicate key handling

Two separate error types to catch:
- `MongoWriteException` with `DUPLICATE_KEY` — on `insertOne`
- `MongoCommandException` with `"DuplicateKey"` — on `findOneAndReplace`

Both map to `AlreadyExistsException`.

## Exception Classes

```kotlin
class OptimisticLockException(message: String) : RuntimeException(message)

// AlreadyExistsException typically comes from a shared library
class AlreadyExistsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

## Retry on Optimistic Lock Failure

When optimistic locking is expected (concurrent writes), wrap with retry:

```kotlin
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    delayMs: Long = 50,
    block: suspend () -> T
): T {
    repeat(maxAttempts - 1) { attempt ->
        try { return block() }
        catch (_: OptimisticLockException) {
            delay(delayMs * (attempt + 1))
        }
    }
    return block() // last attempt — let exception propagate
}

// Usage:
withRetry {
    val product = collection.getProductById(id)
    val updated = updateLogic(product)
    collection.saveProduct(updated, session)
}
```

## BSON Class Version Field

```kotlin
data class ProductBson(
    @BsonId val id: String,
    // ... fields ...
    val version: Int
)
```

The `version` field is a plain `Int` — no special MongoDB annotation needed. It's incremented in application code, not by MongoDB.

## Transactions + Optimistic Locking

When saving multiple documents atomically:

```kotlin
fun ProduceOutput(
    transaction: Transaction<Order>,
    saveOrder: suspend (Order, ClientSession) -> Order,
    saveEvents: suspend (ClientSession, List<OutboxEvent>) -> Unit
): suspend (Decision) -> Response = { decision ->
    val saved = transaction { session ->
        val order = saveOrder(decision.order, session)  // version checked here
        saveEvents(session, decision.events)
        order
    }
    Response(orderId = saved.id)
}
```

If `saveOrder` throws `OptimisticLockException`, the entire transaction is rolled back — events are not saved either.

## Anti-Patterns

### ❌ Manual version check before write

```kotlin
// WRONG — race condition between check and write
val existing = collection.find(doc { "_id" to id }).firstOrNull()
if (existing?.version != expectedVersion) throw OptimisticLockException("...")
collection.replaceOne(doc { "_id" to id }, replacement)
```

### ✅ Atomic check in filter

```kotlin
// RIGHT — single atomic operation
findOneAndReplace(
    filter = doc { "_id" to id; "version" to expectedVersion },
    replacement = bson.copy(version = expectedVersion + 1)
)
```

### ❌ Forgetting version on update path

```kotlin
// WRONG — always inserts, ignores existing version
val bson = product.toBson().copy(version = 1)
collection.insertOne(bson)  // fails with duplicate key on existing docs
```

### ✅ Branch on version

```kotlin
return if (bson.version == 0) {
    insertOne(session, bson.copy(version = 1))  // new
} else {
    findOneAndReplace(/* version in filter */)    // existing
}
```
