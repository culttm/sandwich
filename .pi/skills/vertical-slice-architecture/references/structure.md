# Project Structure

How to organize a Kotlin/Ktor project with Vertical Slice Architecture.

## Canonical Structure

```
src/main/kotlin/com/example/
├── app/
│   ├── Application.kt              ← Ktor entry point
│   └── Routing.kt                  ← top-level route registration
├── orders/
│   ├── placeOrder/
│   │   └── PlaceOrder.kt           ← command slice
│   ├── getOrder/
│   │   └── GetOrder.kt             ← query slice
│   ├── cancelOrder/
│   │   └── CancelOrder.kt          ← command slice
│   └── listOrders/
│       └── ListOrders.kt           ← query slice
├── customers/
│   ├── createCustomer/
│   │   └── CreateCustomer.kt
│   └── getCustomer/
│       └── GetCustomer.kt
├── payments/
│   └── processPayment/
│       ├── ProcessPayment.kt
│       └── PaymentLogic.kt
└── common/
    ├── domain/
    │   ├── OrderRules.kt
    │   └── types/
    │       ├── ProductId.kt
    │       ├── OrderId.kt
    │       └── CustomerId.kt
    ├── validation/
    │   └── OrderValidation.kt
    ├── infra/
    │   ├── Database.kt
    │   ├── HttpResult.kt
    │   └── json.kt
    └── extensions/
        └── BigDecimalExt.kt
```

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Feature group folder | noun, plural, lowercase | `orders/`, `customers/` |
| Slice folder | camelCase verb+noun | `placeOrder/`, `getCustomer/` |
| Main slice file | PascalCase, matches folder | `PlaceOrder.kt` |
| Handler function | PascalCase (factory) | `fun PlaceOrder(...)` |
| Pure logic function | camelCase | `fun buildOrder(...)` |
| Sealed decision | PascalCase + "Decision" | `sealed interface OrderDecision` |
| Request DTO | Slice name + "Request" | `data class PlaceOrderRequest(...)` |
| Response DTO | Slice name + "Response" or domain name | `data class OrderResponse(...)` |

## Ktor Route Registration

Each feature group exposes a routing extension:

```kotlin
// orders/OrderRoutes.kt
fun Route.orderRoutes(db: Database, inventoryRepo: InventoryRepository, orderRepo: OrderRepository) {
    val placeOrder = PlaceOrder(inventoryRepo, orderRepo, PricingRepository(db))
    val getOrder = GetOrder(db)
    val cancelOrder = CancelOrder(orderRepo)
    val listOrders = ListOrders(db)

    route("/orders") {
        post { call.respond(placeOrder(call.receive())) }
        get("/{id}") { call.respond(getOrder(OrderId(call.parameters["id"]!!))) }
        delete("/{id}") { call.respond(cancelOrder(OrderId(call.parameters["id"]!!))) }
        get { call.respond(listOrders(call.receivePageRequest())) }
    }
}

// app/Routing.kt
fun Application.configureRouting() {
    val db = Database(environment.config)
    val inventoryRepo = InventoryRepository(db)
    val orderRepo = OrderRepository(db)
    val customerRepo = CustomerRepository(db)

    routing {
        orderRoutes(db, inventoryRepo, orderRepo)
        customerRoutes(db, customerRepo)
        paymentRoutes(db, orderRepo)
    }
}
```

**Key:** route registration is the **composition root** — where slices are wired with their dependencies.

## Dependency Flow

```
                 ┌──────────┐
                 │ Routing   │ ← composition root
                 └─────┬────┘
            ┌──────────┼──────────┐
            ▼          ▼          ▼
      ┌──────────┐ ┌────────┐ ┌──────────┐
      │ orders/  │ │customs/│ │payments/ │  ← feature groups
      └────┬─────┘ └───┬────┘ └────┬─────┘
           │           │           │
           ▼           ▼           ▼
      ┌─────────────────────────────────┐
      │           common/               │  ← shared pure code
      │  domain/ validation/ infra/     │
      └─────────────────────────────────┘

Rules:
  ✅ Slice → common/         (allowed)
  ✅ Slice → common/infra/   (allowed — DB, HTTP types)
  ❌ Slice → another slice   (forbidden)
  ❌ common/ → any slice     (forbidden)
```

## Migrating From Layered Architecture

### Step 1: Pick one feature

Don't refactor everything at once. Pick one feature (e.g., "place order").

### Step 2: Create the slice folder

```
orders/placeOrder/
└── PlaceOrder.kt
```

### Step 3: Move code into the slice

Copy the controller action, service method, and repo calls into one file.
Structure as a sandwich.

### Step 4: Delete the old layers for this feature

Remove `OrderController.placeOrder()`, `OrderService.placeOrder()`,
and any repo methods only used by this feature.

### Step 5: Repeat per feature

Each feature migrates independently. Old layered code and new slices coexist.

```
src/main/kotlin/com/example/
├── controllers/               ← old (shrinking)
│   └── OrderController.kt    ← only getOrder, listOrders left
├── services/                  ← old (shrinking)
│   └── OrderService.kt       ← only getOrder, listOrders left
├── orders/                    ← new (growing)
│   ├── placeOrder/
│   │   └── PlaceOrder.kt     ← migrated
│   └── cancelOrder/
│       └── CancelOrder.kt    ← migrated
└── common/
```

### Step 6: Remove empty layers

When all features from `OrderController` are migrated, delete the file.
When `controllers/` is empty, delete the folder.

## Feature Group Boundaries

When to create a new feature group folder:

| Signal | Action |
|---|---|
| Different aggregate root | New folder: `orders/`, `customers/` |
| Different bounded context | New folder: `billing/`, `shipping/` |
| > 8 slices in one group | Consider splitting by subdomain |
| Shared database table | Same group is probably fine |
| No shared data at all | Definitely separate groups |

## Scaling: From Monolith to Modules

When the project grows, feature groups can become modules:

```
Monolith (single project):           Modular (multi-module):
src/                                  orders/
├── orders/                             ├── src/main/kotlin/
│   ├── placeOrder/                     │   ├── placeOrder/
│   └── getOrder/                       │   └── getOrder/
├── customers/                          └── build.gradle.kts
│   └── ...                           customers/
└── common/                             ├── src/main/kotlin/
                                        │   └── ...
                                        └── build.gradle.kts
                                       common/
                                        ├── src/main/kotlin/
                                        └── build.gradle.kts
```

Each module depends only on `common`. Slices within a module stay independent.
