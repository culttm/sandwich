# Queries

Building MongoDB queries with the Kotlin coroutine driver.

## `doc { }` DSL

Use a `doc` builder for type-safe BSON documents:

```kotlin
import org.bson.Document

// Simple equality
doc { "status" to "ACTIVE" }

// Nested operator
doc { "price" to { "\$gte" to 100 } }

// Multiple conditions (implicit AND within one doc)
doc {
    "status" to "ACTIVE"
    "category" to "electronics"
}
```

If your project doesn't have a `doc` DSL, use `Document` directly:

```kotlin
Document("status", "ACTIVE")
Document("\$and", listOf(Document("status", "ACTIVE"), Document("price", Document("\$gte", 100))))
```

## Dynamic Filter Building

The core pattern: build `listOfNotNull`, then combine.

```kotlin
suspend fun MongoCollection<OrderBson>.searchOrders(
    customerId: String? = null,
    status: OrderStatus? = null,
    createdAfter: LocalDateTime? = null,
    minTotal: BigDecimal? = null,
    afterId: String? = null,
    limit: Int = 20,
    offset: Int = 0
): List<Order> {
    val filters = listOfNotNull(
        customerId?.let { doc { "customer_id" to it } },
        status?.let { doc { "status" to it.name } },
        createdAfter?.let { doc { "created_at" to { "\$gte" to it } } },
        minTotal?.let { doc { "total.amount" to { "\$gte" to Decimal128(it) } } },
        afterId?.let { doc { "_id" to { "\$gt" to it } } }
    )

    val filter = when {
        filters.isEmpty() -> Document()
        filters.size == 1 -> filters.first()
        else -> doc { "\$and" to filters }
    }

    return find(filter)
        .sort(Sorts.descending("created_at"))
        .skip(offset)
        .limit(limit)
        .toList()
        .map { it.toDomain() }
}
```

### Why `listOfNotNull`?

Each filter is included **only if the parameter is non-null**. This gives optional filtering for free — callers pass only what they need:

```kotlin
// All orders for customer
searchOrders(customerId = "cust-1")

// Active orders after a date
searchOrders(status = OrderStatus.ACTIVE, createdAfter = someDate)

// Everything (no filters)
searchOrders()
```

## Common Query Patterns

### Equality

```kotlin
doc { "customer_id" to "cust-123" }
```

### In-list

```kotlin
doc { "_id" to { "\$in" to listOf("id-1", "id-2", "id-3") } }
```

### Range

```kotlin
doc { "price" to { "\$gte" to 100; "\$lte" to 500 } }
```

### Date range

```kotlin
doc {
    "created_at" to { "\$gte" to startDate }
    "expires_at" to { "\$lte" to endDate }
}

// Active at a point in time
doc {
    "active_from" to { "\$lte" to pointInTime }
    "active_till" to { "\$gte" to pointInTime }
}
```

### Nested field

```kotlin
doc { "items.product_id" to { "\$in" to productIds } }
doc { "shipping.address.city" to "Berlin" }
```

### Exists / not null

```kotlin
doc { "deleted_at" to null }                           // field is null
doc { "metadata" to { "\$exists" to true } }          // field exists
doc { "metadata" to { "\$exists" to false } }         // field doesn't exist
```

### OR conditions

```kotlin
doc {
    "\$or" to listOf(
        doc { "status" to "PENDING" },
        doc { "status" to "PROCESSING" }
    )
}
```

### Combining AND + OR

```kotlin
doc {
    "\$and" to listOf(
        doc { "customer_id" to customerId },
        doc {
            "\$or" to listOf(
                doc { "status" to "ACTIVE" },
                doc { "status" to "PENDING" }
            )
        }
    )
}
```

## Sorting

```kotlin
// Single field
.sort(Sorts.descending("created_at"))

// Multiple fields
.sort(Sorts.orderBy(
    Sorts.ascending("status"),
    Sorts.descending("created_at")
))
```

## Pagination

### Offset-based (simple, for admin/internal)

```kotlin
.skip(offset)
.limit(limit)
```

### Cursor-based (better for large datasets)

```kotlin
// Use last seen _id as cursor
afterId?.let { doc { "_id" to { "\$gt" to it } } }

// Always sort by _id for stable pagination
.sort(Sorts.ascending("_id"))
.limit(limit)
```

## Aggregations

For complex queries, use the aggregation pipeline:

```kotlin
suspend fun MongoCollection<OrderBson>.getOrderStats(
    sellerId: String,
    from: LocalDateTime,
    to: LocalDateTime
): OrderStats {
    val pipeline = listOf(
        Aggregates.match(
            Filters.and(
                Filters.eq("seller_id", sellerId),
                Filters.gte("created_at", from),
                Filters.lt("created_at", to)
            )
        ),
        Aggregates.group(
            "\$status",
            Accumulators.sum("count", 1),
            Accumulators.sum("total", "\$total.amount")
        )
    )

    return aggregate(pipeline)
        .toList()
        .let { results -> mapToOrderStats(results) }
}
```

## Update Operations

### Update specific fields (no version check)

```kotlin
suspend fun MongoCollection<OutboxEventBson>.markAsPublished(
    eventIds: List<String>,
    publishedAt: LocalDateTime = LocalDateTime.now()
) {
    if (eventIds.isEmpty()) return
    updateMany(
        doc { "_id" to { "\$in" to eventIds } },
        doc { "\$set" to { "published_at" to publishedAt } }
    )
}
```

### Push to array

```kotlin
updateOne(
    doc { "_id" to orderId },
    doc { "\$push" to { "items" to newItemBson } }
)
```

### Increment

```kotlin
updateOne(
    doc { "_id" to productId },
    doc { "\$inc" to { "stock_count" to -quantity } }
)
```

## Using `Filters` API (alternative to `doc {}`)

The driver also provides a typed `Filters` API:

```kotlin
import com.mongodb.client.model.Filters

Filters.eq("_id", id)
Filters.`in`("_id", ids)
Filters.gte("created_at", date)
Filters.and(Filters.eq("status", "ACTIVE"), Filters.gte("price", 100))
Filters.or(Filters.eq("status", "PENDING"), Filters.eq("status", "PROCESSING"))
```

Both `doc {}` DSL and `Filters` API are valid. Pick one and be consistent.

## Anti-Patterns

### ❌ Building filter strings

```kotlin
// WRONG — injection risk, unreadable
val filter = Document.parse("""{"status": "$status"}""")
```

### ✅ Use typed builders

```kotlin
doc { "status" to status }
// or
Filters.eq("status", status)
```

### ❌ Fetching all then filtering in Kotlin

```kotlin
// WRONG — loads entire collection into memory
collection.find().toList().filter { it.status == "ACTIVE" }
```

### ✅ Filter in MongoDB

```kotlin
collection.find(doc { "status" to "ACTIVE" }).toList()
```

### ❌ N+1 queries in a loop

```kotlin
// WRONG — one query per ID
ids.map { id -> collection.find(doc { "_id" to id }).firstOrNull() }
```

### ✅ Batch with $in

```kotlin
collection.find(doc { "_id" to { "\$in" to ids } }).toList()
```
