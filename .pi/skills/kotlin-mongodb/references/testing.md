# Testing with MongoDB

Integration tests using TestContainers. No mocked MongoDB — test against real engine.

## Setup: TestContainers

### Dependencies (Gradle)

```toml
# libs.versions.toml
[versions]
testcontainers = "1.20.4"

[libraries]
testcontainers-mongodb = { module = "org.testcontainers:mongodb", version.ref = "testcontainers" }
```

```kotlin
// build.gradle.kts
testImplementation(libs.testcontainers.mongodb)
```

**Note:** `testcontainers-junit` (`@Testcontainers`/`@Container`) is NOT needed.
We use a lazy singleton instead — simpler, faster, no per-class container overhead.

### Shared Container — Lazy Singleton

One container for ALL tests. Started once on first access. Each test gets an isolated database:

```kotlin
// test/kotlin/com/example/TestDatabase.kt
import org.testcontainers.containers.MongoDBContainer

class TestDatabase {
    companion object {
        val db by lazy {
            MongoDBContainer("mongo:7").also { it.start() }
        }

        val connectionString: String by lazy { db.connectionString }
    }
}
```

**Key:** `by lazy` means the container starts only when first test accesses it.
No `@Testcontainers`, no `@Container`, no `companion object` with `@JvmStatic`.

### Per-Test Database Isolation

Each test class (or test method) creates a unique database name:

```kotlin
import com.mongodb.kotlin.client.coroutine.MongoClient

// In @BeforeEach:
val database = MongoClient.create(TestDatabase.connectionString)
    .getDatabase("test-${System.nanoTime()}")
```

This ensures **zero test pollution** — no cleanup needed, no `@AfterEach` drops.

## Test Structure

### Unit tests: pure logic — no MongoDB

Pure `decide` functions are tested with plain data. No database, no TestContainers:

```kotlin
class CreateOrderLogicTest {
    @Test
    fun `creates order with correct total`() {
        val input = CreateOrderInput(
            orderId = "order-1",
            items = listOf(ItemRequest("prod-1", quantity = 2)),
            products = mapOf("prod-1" to Product("prod-1", "Widget", price = 100)),
            now = Instant.parse("2024-01-01T00:00:00Z")
        )

        val decision = createOrder(input)

        assertIs<CreateOrderDecision.Created>(decision)
        assertEquals(200, decision.order.total)
    }
}
```

### Integration tests: collection operations

Test BSON mapping + queries against real MongoDB:

```kotlin
class ProductCollectionTest {
    private lateinit var collection: MongoCollection<ProductBson>

    @BeforeEach
    fun setUp() = runBlocking {
        val db = MongoClient.create(TestDatabase.connectionString)
            .getDatabase("test-${System.nanoTime()}")
        collection = db.getCollection<ProductBson>("products").ensureIndexes()
    }

    @Test
    fun `saves and retrieves product`() = runTest {
        val product = Product(id = "p-1", name = "Widget", price = Money(100), version = 0)

        val saved = collection.saveProduct(product)

        assertEquals(1, saved.version)
        val found = collection.getProductById("p-1")
        assertEquals("Widget", found.name)
    }

    @Test
    fun `optimistic locking prevents concurrent modification`() = runTest {
        val product = Product(id = "p-1", name = "Widget", price = Money(100), version = 0)
        collection.saveProduct(product)

        val v1 = collection.getProductById("p-1")  // version = 1

        // Simulate concurrent write
        collection.saveProduct(v1.copy(name = "Updated"))  // version → 2

        // Stale write should fail
        assertFailsWith<OptimisticLockException> {
            collection.saveProduct(v1.copy(name = "Stale"))  // still version 1
        }
    }

    @Test
    fun `search filters by category`() = runTest {
        collection.saveProduct(product("p-1", category = "A"))
        collection.saveProduct(product("p-2", category = "B"))
        collection.saveProduct(product("p-3", category = "A"))

        val results = collection.searchProducts(categoryId = "A")

        assertEquals(2, results.size)
        assertTrue(results.all { it.category == "A" })
    }
}
```

### E2E tests: full app with MongoDB

Test the entire HTTP/gRPC flow with real MongoDB.
Use abstract scenario class for test DSL — concrete implementations for E2E vs unit.

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFlowE2ETest {
    private lateinit var database: MongoDatabase
    private lateinit var teardown: () -> Unit
    private lateinit var client: HttpClient

    @BeforeEach
    fun setUp() {
        database = MongoClient.create(TestDatabase.connectionString)
            .getDatabase("e2e-${System.nanoTime()}")
        runBlocking { database.seed() }
        teardown = runBlocking { ServerApp(database)() }
        client = HttpClient { install(ContentNegotiation) { json() } }
    }

    @AfterEach
    fun tearDown() {
        client.close()
        teardown()
    }

    @Test
    fun `create order and pay`() = runTest {
        val orderId = createOrder(customer = "Alice", items = listOf("prod-1"))
        expectOrderStatus(orderId, "DRAFT")

        setShipping(orderId, address = "123 Main St", phone = "+1234567890")
        pay(orderId, method = "CARD")

        expectOrderStatus(orderId, "PAID")
    }
}
```

## Test Data Helpers

### BSON fixtures

```kotlin
fun productBson(
    id: String = "prod-${UUID.randomUUID()}",
    name: String = "Test Product",
    price: BigDecimal = BigDecimal("10.00"),
    category: String = "default",
    version: Int = 1
) = ProductBson(
    id = id,
    displayName = name,
    unitPrice = MoneyBson(Decimal128(price), "USD"),
    category = category,
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now(),
    version = version
)
```

### Seed data function

```kotlin
suspend fun MongoDatabase.seedTestData() {
    val products = getCollection<ProductBson>("products")
    products.insertMany(listOf(
        productBson("widget-1", "Small Widget", BigDecimal("5.00")),
        productBson("widget-2", "Large Widget", BigDecimal("15.00")),
        productBson("gadget-1", "Basic Gadget", BigDecimal("25.00"))
    ))
}
```

## Direct DB Assertions

In E2E tests, verify state directly in MongoDB:

```kotlin
override suspend fun expectOrderStatus(orderId: String, expected: String) {
    val order = orderCollection.getOrderById(orderId)
    assertEquals(expected, order.status.name)
}

// Or via HTTP if testing the full stack:
override suspend fun expectOrderStatus(orderId: String, expected: String) {
    val response = client.get("$baseUrl/orders/$orderId")
    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertEquals(expected, body["status"]!!.jsonPrimitive.content)
}
```

## Tips

### Parallel test safety

Each test creates a unique database name → tests can run in parallel:

```kotlin
val db = client.getDatabase("test-${System.nanoTime()}")
```

### Don't clean up — use unique databases

```kotlin
// ❌ WRONG — slow, error-prone
@AfterEach fun cleanup() = runBlocking { collection.drop() }

// ✅ RIGHT — each test gets fresh database
@BeforeEach fun setUp() {
    db = MongoClient.create(TestDatabase.connectionString)
        .getDatabase("test-${System.nanoTime()}")
}
```

## Anti-Patterns

### ❌ `@Testcontainers` + `@Container` per class

```kotlin
// WRONG — each class gets its own container lifecycle management
// Slow, verbose, unnecessary boilerplate
@Testcontainers
class SomeTest {
    companion object {
        @Container @JvmStatic
        val mongo = MongoDBContainer("mongo:7")
    }
}
```

### ✅ Lazy singleton — one container for all tests

```kotlin
// RIGHT — TestDatabase.kt singleton, started once on first access
class TestDatabase {
    companion object {
        val db by lazy { MongoDBContainer("mongo:7").also { it.start() } }
        val connectionString: String by lazy { db.connectionString }
    }
}

// In any test:
val db = MongoClient.create(TestDatabase.connectionString)
    .getDatabase("test-${System.nanoTime()}")
```

### ❌ Mocking MongoCollection

```kotlin
// WRONG — tests pass but queries may be broken
val mockCollection = mockk<MongoCollection<ProductBson>>()
every { mockCollection.find(any()) } returns flowOf(productBson)
```

### ✅ Test against real MongoDB

```kotlin
// RIGHT — catches BSON mapping bugs, index issues, query errors
val collection = testDb.getCollection<ProductBson>("products")
collection.insertOne(productBson)
val result = collection.getProductById("p-1")
```

### ❌ Shared mutable state between tests

```kotlin
// WRONG — test order matters, flaky
companion object {
    val collection = setupOnce()  // shared across all tests
}
```

### ✅ Isolated database per test

```kotlin
@BeforeEach fun setUp() {
    db = MongoClient.create(TestDatabase.connectionString)
        .getDatabase("test-${System.nanoTime()}")
    collection = db.getCollection<ProductBson>("products")
}
```
