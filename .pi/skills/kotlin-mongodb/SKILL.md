---
name: kotlin-mongodb
description: >
  MongoDB with Kotlin coroutine driver. Use when adding MongoDB collections,
  writing queries, creating BSON mappings, implementing save with optimistic locking,
  building indexes, wiring collections into features, or setting up TestContainers.
---

# Kotlin MongoDB

Work with MongoDB using the official Kotlin coroutine driver + BSON POJOs.

## The One Rule

> **Domain models never import MongoDB.** BSON data classes live in a separate layer; mapping is explicit via `.toDomain()` / `.toBson()`.

## Architecture Overview

```
common/database/bson/              ← BSON data classes + domain↔bson mapping
common/database/collection/<name>/ ← extension functions on MongoCollection<T>
feature/*/                         ← Gather/Produce receive collection ops via lambdas
apps/                              ← MongoClient, getCollection, ensureIndexes, wiring
```

**No Repository interfaces.** Each DB operation is a standalone extension function on `MongoCollection<BsonType>`.

## BSON Data Classes

BSON classes mirror domain but carry MongoDB annotations:

```kotlin
// common/database/bson/ProductBson.kt
data class ProductBson(
    @BsonId
    val id: String,
    @BsonProperty("display_name")
    val displayName: String,
    @BsonProperty("unit_price")
    val unitPrice: MoneyBson,
    @BsonProperty("created_at")
    val createdAt: LocalDateTime,
    @BsonProperty("updated_at")
    val updatedAt: LocalDateTime,
    val version: Int
) {
    fun toDomain() = Product(
        id = id,
        displayName = displayName,
        unitPrice = unitPrice.toDomain(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version
    )
}

fun Product.toBson() = ProductBson(
    id = id,
    displayName = displayName,
    unitPrice = unitPrice.toBson(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version
)
```

### Value Objects — custom BSON mapping

```kotlin
data class MoneyBson(
    val amount: Decimal128,
    @BsonProperty("currency_code")
    val currencyCode: String
) {
    fun toDomain() = Money(
        amount = amount.bigDecimalValue(),
        currency = Currency.valueOf(currencyCode)
    )
}

fun Money.toBson() = MoneyBson(
    amount = Decimal128(amount),
    currencyCode = currency.name
)
```

### Nested documents

```kotlin
data class OrderBson(
    @BsonId val id: String,
    val items: List<OrderItemBson>,        // embedded list
    val shipping: ShippingBson?,           // nullable embedded
    @BsonProperty("created_at") val createdAt: LocalDateTime,
    val version: Int
) {
    fun toDomain() = Order(
        id = id,
        items = items.map { it.toDomain() },
        shipping = shipping?.toDomain(),
        createdAt = createdAt,
        version = version
    )
}
```

### `@param:BsonProperty` vs `@BsonProperty`

Use `@param:BsonProperty` when the BSON POJO codec reads via **constructor parameters** (recommended for immutable data classes):

```kotlin
data class UserProfile(
    @param:BsonId val id: UUID,
    @param:BsonProperty("display_name") val displayName: String,
    @param:BsonProperty("updated_at") val updatedAt: LocalDateTime
)
```

Plain `@BsonProperty` works when the codec uses **field access**. Both work — be consistent within a project.

## Collection Operations — Extension Functions

Each operation = one file. No repository classes.

### Find by ID

```kotlin
// common/database/collection/product/GetProductById.kt
suspend fun MongoCollection<ProductBson>.getProductById(id: String): Product =
    find(doc { "_id" to id }).firstOrNull()
        ?.toDomain()
        ?: throw NotFoundException("Product $id not found")
```

### Search with filters

```kotlin
// common/database/collection/product/SearchProducts.kt
suspend fun MongoCollection<ProductBson>.searchProducts(
    categoryId: String? = null,
    minPrice: BigDecimal? = null,
    afterId: String? = null,
    limit: Int = 20
): List<Product> {
    val filters = listOfNotNull(
        categoryId?.let { doc { "category_id" to it } },
        minPrice?.let { doc { "unit_price.amount" to { "\$gte" to Decimal128(it) } } },
        afterId?.let { doc { "_id" to { "\$gt" to it } } }
    )

    val filter = when {
        filters.isEmpty() -> Document()
        filters.size == 1 -> filters.first()
        else -> doc { "\$and" to filters }
    }

    return find(filter)
        .sort(Sorts.ascending("_id"))
        .limit(limit)
        .toList()
        .map { it.toDomain() }
}
```

### Save with Optimistic Locking

→ See [optimistic-locking.md](references/optimistic-locking.md) for full pattern

```kotlin
// common/database/collection/product/SaveProduct.kt
suspend fun MongoCollection<ProductBson>.saveProduct(
    product: Product,
    session: ClientSession,
    now: LocalDateTime = LocalDateTime.now()
): Product {
    val bson = product.toBson()
    return if (bson.version == 0) {
        val toInsert = bson.copy(version = 1)
        insertOne(session, toInsert)
        toInsert.toDomain()
    } else {
        val result = findOneAndReplace(
            session,
            filter = doc { "_id" to product.id; "version" to product.version },
            replacement = bson.copy(version = bson.version + 1, updatedAt = now),
            options = FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)
        )
        result?.toDomain()
            ?: throw OptimisticLockException("Product ${product.id} was modified")
    }
}
```

## Indexes

Indexes declared as extension function, called once at startup:

```kotlin
// common/database/collection/product/ProductIndexes.kt
suspend fun MongoCollection<ProductBson>.ensureIndexes() = also {
    createIndex(
        Indexes.ascending("category_id"),
        IndexOptions().name("category_id__index").background(true)
    )
    createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("category_id"),
            Indexes.ascending("unit_price.amount")
        ),
        IndexOptions().name("category_id_1_unit_price_amount_1").background(true)
    )
    createIndex(
        Indexes.descending("created_at"),
        IndexOptions().name("created_at_desc__index").background(true)
    )
}
```

### Index naming convention
- Single field: `field_name__index`
- Compound: `field1_1_field2_-1` (1 = asc, -1 = desc)
- Always `.background(true)` for production safety

## Wiring in App

```kotlin
fun ServerApp(config: AppConfig) = App {
    val mongoClient = MongoClient(config.mongodb.uri)
    val db = mongoClient.getDatabase(config.mongodb.database)

    val productCollection = db.getCollection<ProductBson>("products").ensureIndexes()
    val orderCollection = db.getCollection<OrderBson>("orders").ensureIndexes()

    // Wire into features via method references
    val server = createServer {
        createOrderRoute(
            handler = CreateOrderHandler(
                gatherInput = GatherCreateOrderInput(
                    readProducts = { ids -> productCollection.searchProducts(ids) }
                ),
                decide = ::createOrder,
                produceOutput = ProduceCreateOrderOutput(
                    saveOrder = orderCollection::saveOrder
                )
            )
        )
    }
    // ...
}
```

Features receive **operations as lambdas**, never raw `MongoCollection`:

```kotlin
fun GatherInput(
    findProduct: suspend (String) -> Product?,    // ← lambda, not MongoCollection
    findOrder: suspend (String) -> Order?
): suspend (Request) -> Input = { request -> /* ... */ }
```

## Transactions

Multi-document transactions via `ClientSession`:

```kotlin
// Library provides Transaction<T> type alias
typealias Transaction<T> = suspend (suspend (ClientSession) -> T) -> T

// Usage in ProduceOutput:
fun ProduceApplyOutput(
    transaction: Transaction<Result>,
    saveOrder: suspend (Order, ClientSession) -> Order,
    saveEvents: suspend (ClientSession, List<Event>) -> Unit
): suspend (Decision) -> Response = { decision ->
    val saved = transaction { session ->
        val order = saveOrder(decision.order, session)
        saveEvents(session, decision.events)
        order
    }
    Response(orderId = saved.id)
}

// Wiring:
produceOutput = ProduceApplyOutput(
    transaction = mongoClient::transaction,
    saveOrder = orderCollection::saveOrder,
    saveEvents = outboxCollection::saveEventsBatch
)
```

## Decision Tree

### Adding a new collection
→ Create BsonType, mapping functions, ensureIndexes, collection operations
→ See [collection-setup.md](references/collection-setup.md)

### Implementing save with versioning
→ Use optimistic locking pattern with version field
→ See [optimistic-locking.md](references/optimistic-locking.md)

### Writing complex queries
→ Build filters dynamically with `listOfNotNull` + `doc { }` DSL
→ See [queries.md](references/queries.md)

### Setting up tests
→ TestContainers for integration, direct collection access for verification
→ See [testing.md](references/testing.md)

## Anti-Patterns

### ❌ Domain model with BSON annotations

```kotlin
// WRONG — domain depends on MongoDB
@Serializable
data class Product(
    @BsonId val id: String,                    // MongoDB leak!
    @BsonProperty("name") val name: String     // MongoDB leak!
)
```

### ✅ Separate BSON and domain

```kotlin
// Domain — clean
data class Product(val id: String, val name: String)

// BSON — database concern
data class ProductBson(@BsonId val id: String, val name: String) {
    fun toDomain() = Product(id, name)
}
```

### ❌ Repository class with injected collection

```kotlin
// WRONG — unnecessary abstraction layer
class ProductRepository(private val collection: MongoCollection<ProductBson>) {
    suspend fun findById(id: String): Product? = /* ... */
    suspend fun save(product: Product) { /* ... */ }
}
```

### ✅ Extension functions — no wrapping

```kotlin
// RIGHT — direct, composable, no class ceremony
suspend fun MongoCollection<ProductBson>.getProductById(id: String): Product = /* ... */
suspend fun MongoCollection<ProductBson>.saveProduct(product: Product, session: ClientSession) = /* ... */
```

### ❌ Passing MongoCollection to features

```kotlin
// WRONG — feature knows about MongoDB
fun GatherInput(
    collection: MongoCollection<ProductBson>    // ← leaking infra!
): suspend (Request) -> Input = { /* ... */ }
```

### ✅ Pass lambdas — feature knows nothing about MongoDB

```kotlin
// RIGHT — feature sees only suspend functions
fun GatherInput(
    findProduct: suspend (String) -> Product?   // ← clean contract
): suspend (Request) -> Input = { /* ... */ }
```

### ❌ Forgetting session in transactions

```kotlin
// WRONG — writes outside transaction
transaction { session ->
    val order = saveOrder(order, session)
    saveEvents(events)          // ← not in transaction!
}
```

### ✅ All writes through session

```kotlin
transaction { session ->
    val order = saveOrder(order, session)
    saveEvents(session, events) // ← in transaction
}
```

## Quality Check

Before finishing any MongoDB implementation, verify:

1. Domain models have **zero MongoDB imports** (`@BsonId`, `@BsonProperty`, `Decimal128` — all in BSON layer)
2. BSON ↔ domain mapping is **explicit** (`.toDomain()` / `.toBson()`)
3. Each collection operation is an **extension function**, not a repository method
4. Features receive **lambdas**, not `MongoCollection` instances
5. Indexes are declared in `ensureIndexes()` — called once at startup
6. Save operations use **optimistic locking** (version field + `findOneAndReplace`)
7. Multi-document writes use **transactions** with `ClientSession`
8. TestContainers used for integration tests — no embedded/mocked MongoDB
