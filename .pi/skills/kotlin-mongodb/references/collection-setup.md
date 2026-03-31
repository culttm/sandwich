# Collection Setup

Step-by-step guide for adding a new MongoDB collection.

## Checklist

1. Create BSON data class with `@BsonId` + `@BsonProperty`
2. Add `.toDomain()` method + `.toBson()` extension
3. Create `ensureIndexes()` extension function
4. Create collection operation files (find, save, search)
5. Wire `getCollection<BsonType>("name").ensureIndexes()` in app startup
6. Pass operations as lambdas to features

## Step 1: BSON Data Class

```kotlin
// common/database/bson/InvoiceBson.kt
package com.example.common.database.bson

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.Decimal128
import java.time.LocalDateTime

data class InvoiceBson(
    @BsonId
    val id: String,
    @BsonProperty("customer_id")
    val customerId: String,
    @BsonProperty("line_items")
    val lineItems: List<InvoiceLineItemBson>,
    @BsonProperty("total_amount")
    val totalAmount: MoneyBson,
    val status: String,
    @BsonProperty("issued_at")
    val issuedAt: LocalDateTime,
    @BsonProperty("created_at")
    val createdAt: LocalDateTime,
    @BsonProperty("updated_at")
    val updatedAt: LocalDateTime,
    val version: Int
) {
    fun toDomain() = Invoice(
        id = id,
        customerId = customerId,
        lineItems = lineItems.map { it.toDomain() },
        totalAmount = totalAmount.toDomain(),
        status = InvoiceStatus.valueOf(status),
        issuedAt = issuedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version
    )
}

data class InvoiceLineItemBson(
    @BsonProperty("product_id")
    val productId: String,
    val quantity: Int,
    @BsonProperty("unit_price")
    val unitPrice: MoneyBson,
    @BsonProperty("line_total")
    val lineTotal: MoneyBson
) {
    fun toDomain() = InvoiceLineItem(
        productId = productId,
        quantity = quantity,
        unitPrice = unitPrice.toDomain(),
        lineTotal = lineTotal.toDomain()
    )
}

// ── Domain → BSON ──

fun Invoice.toBson() = InvoiceBson(
    id = id,
    customerId = customerId,
    lineItems = lineItems.map { it.toBson() },
    totalAmount = totalAmount.toBson(),
    status = status.name,
    issuedAt = issuedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version
)

fun InvoiceLineItem.toBson() = InvoiceLineItemBson(
    productId = productId,
    quantity = quantity,
    unitPrice = unitPrice.toBson(),
    lineTotal = lineTotal.toBson()
)
```

### Mapping rules

| Domain type | BSON type | Notes |
|---|---|---|
| `String` | `String` | Direct |
| `Int`, `Long` | `Int`, `Long` | Direct |
| `BigDecimal` | `Decimal128` | `Decimal128(value)` / `.bigDecimalValue()` |
| `UUID` | `String` | `.toString()` / `UUID.fromString()` |
| `Enum` | `String` | `.name` / `Enum.valueOf()` |
| `LocalDateTime` | `LocalDateTime` | Direct (driver handles BSON Date) |
| `Instant` | `Instant` | Direct |
| Value object (`Money`) | Nested BSON class (`MoneyBson`) | Custom `.toDomain()` / `.toBson()` |
| `List<T>` | `List<TBson>` | `.map { it.toDomain() }` |
| `T?` | `T?` | Nullable — MongoDB stores `null` |

## Step 2: Indexes

```kotlin
// common/database/collection/invoice/InvoiceIndexes.kt
package com.example.common.database.collection.invoice

import com.example.common.database.bson.InvoiceBson
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection

suspend fun MongoCollection<InvoiceBson>.ensureIndexes() = also {
    createIndex(
        Indexes.ascending("customer_id"),
        IndexOptions()
            .name("customer_id__index")
            .background(true)
    )
    createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("customer_id"),
            Indexes.descending("issued_at")
        ),
        IndexOptions()
            .name("customer_id_1_issued_at_-1")
            .background(true)
    )
    createIndex(
        Indexes.ascending("status"),
        IndexOptions()
            .name("status__index")
            .background(true)
    )
}
```

### Index types

| Need | Index type | Example |
|---|---|---|
| Exact match | `Indexes.ascending("field")` | `customer_id` |
| Range + sort | `Indexes.compoundIndex(asc, desc)` | `customer_id + issued_at` |
| Unique constraint | `IndexOptions().unique(true)` | `email` |
| TTL auto-delete | `IndexOptions().expireAfter(14, TimeUnit.DAYS)` | `created_at` |
| Partial (conditional) | `IndexOptions().partialFilterExpression(doc)` | Only active records |
| Sparse (skip nulls) | `IndexOptions().sparse(true)` | Optional field |

## Step 3: Collection Operations

### Find

```kotlin
// common/database/collection/invoice/GetInvoiceById.kt
suspend fun MongoCollection<InvoiceBson>.getInvoiceById(id: String): Invoice =
    find(doc { "_id" to id }).firstOrNull()
        ?.toDomain()
        ?: throw NotFoundException("Invoice $id not found")
```

### Save (with optimistic locking)

```kotlin
// common/database/collection/invoice/SaveInvoice.kt
suspend fun MongoCollection<InvoiceBson>.saveInvoice(
    invoice: Invoice,
    session: ClientSession,
    now: LocalDateTime = LocalDateTime.now()
): Invoice {
    val bson = invoice.toBson()
    return if (bson.version == 0) {
        val doc = bson.copy(version = 1, createdAt = now, updatedAt = now)
        insertOne(session, doc)
        doc.toDomain()
    } else {
        findOneAndReplace(
            session,
            filter = doc { "_id" to invoice.id; "version" to invoice.version },
            replacement = bson.copy(version = bson.version + 1, updatedAt = now),
            options = FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)
        )?.toDomain()
            ?: throw OptimisticLockException("Invoice ${invoice.id} version conflict")
    }
}
```

### Search

```kotlin
// common/database/collection/invoice/SearchInvoices.kt
suspend fun MongoCollection<InvoiceBson>.searchInvoices(
    customerId: String? = null,
    status: InvoiceStatus? = null,
    issuedAfter: LocalDateTime? = null,
    limit: Int = 20,
    offset: Int = 0
): List<Invoice> {
    val filters = listOfNotNull(
        customerId?.let { doc { "customer_id" to it } },
        status?.let { doc { "status" to it.name } },
        issuedAfter?.let { doc { "issued_at" to { "\$gte" to it } } }
    )
    val filter = when {
        filters.isEmpty() -> Document()
        filters.size == 1 -> filters.first()
        else -> doc { "\$and" to filters }
    }
    return find(filter)
        .sort(Sorts.descending("issued_at"))
        .skip(offset)
        .limit(limit)
        .toList()
        .map { it.toDomain() }
}
```

## Step 4: Wire in App

```kotlin
// apps/ServerApp.kt
val mongoClient = MongoClient(config.mongodb.uri)
val db = mongoClient.getDatabase(config.mongodb.database)

val invoiceCollection = db.getCollection<InvoiceBson>("invoices").ensureIndexes()

// Wire into feature:
createInvoiceRoute(
    handler = CreateInvoiceHandler(
        gatherInput = GatherInput(
            findCustomer = customerCollection::getCustomerById
        ),
        decide = ::createInvoice,
        produceOutput = ProduceOutput(
            saveInvoice = invoiceCollection::saveInvoice,
            transaction = mongoClient::transaction
        )
    )
)
```

## File structure result

```
common/database/
├── bson/
│   ├── InvoiceBson.kt         ← data class + toDomain() + toBson()
│   └── MoneyBson.kt           ← value object mapping
└── collection/
    └── invoice/
        ├── InvoiceIndexes.kt  ← ensureIndexes()
        ├── GetInvoiceById.kt  ← single find
        ├── SaveInvoice.kt     ← insert or optimistic replace
        └── SearchInvoices.kt  ← filtered search
```
