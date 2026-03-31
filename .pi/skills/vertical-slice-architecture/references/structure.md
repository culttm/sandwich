# Project Structure

How to organize a Kotlin/Ktor project with Vertical Slice Architecture.

## Canonical Structure

```
src/main/kotlin/com/example/
├── apps/
│   └── AppServer.kt                   ← composition root + route registration
├── features/
│   ├── catalog/
│   │   └── getProducts/
│   │       └── GetProducts.kt         ← query slice (single file)
│   └── orders/
│       ├── DomainError.kt             ← shared error vocabulary for all order slices
│       ├── createOrder/               ← command slice (5 files)
│       │   ├── CreateOrder.kt          ← HTTP DTOs + route (wiring + protocol)
│       │   ├── Domain.kt              ← Input + Decision + pure logic
│       │   ├── CreateOrderHandler.kt  ← orchestrator
│       │   ├── GatherCreateOrderInput.kt
│       │   └── ProduceCreateOrderOutput.kt
│       ├── assignShipping/            ← command slice
│       │   ├── AssignShipping.kt
│       │   ├── Domain.kt
│       │   ├── AssignShippingHandler.kt
│       │   ├── GatherAssignShippingInput.kt
│       │   └── ProduceAssignShippingOutput.kt
│       ├── payOrder/                  ← command slice
│       │   └── ...
│       ├── cancelOrder/               ← command slice
│       │   └── ...
│       └── getOrder/
│           └── GetOrder.kt            ← query slice (single file)
└── common/                            ← minimal shared code
    ├── domain/
    │   ├── PricingRules.kt            ← pure business rules
    │   └── Types.kt                   ← shared domain types (Order, OrderStatus, etc.)
    ├── http/
    │   ├── ErrorHandling.kt           ← StatusPages config
    │   ├── HttpServer.kt             ← Ktor server factory
    │   ├── Monitoring.kt
    │   └── Serialization.kt
    ├── infra/
    │   └── Db.kt                      ← data store
    └── app/
        └── App.kt                     ← lifecycle management
```

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Feature group folder | noun, plural, lowercase | `orders/`, `catalog/` |
| Slice folder | camelCase verb+noun | `createOrder/`, `getOrder/` |
| Entry point file | PascalCase, matches folder | `CreateOrder.kt` |
| Domain file | Always `Domain.kt` | `Domain.kt` |
| Handler | `{SliceName}Handler.kt` | `CreateOrderHandler.kt` |
| GatherInput | `Gather{SliceName}Input.kt` | `GatherCreateOrderInput.kt` |
| ProduceOutput | `Produce{SliceName}Output.kt` | `ProduceCreateOrderOutput.kt` |
| Pure logic function | `fun {sliceName}(input)` | `fun createOrder(input)` |
| Sealed decision | PascalCase + "Decision" | `sealed interface CreateOrderDecision` |
| Input type | PascalCase + "Input" | `data class CreateOrderInput(...)` |
| Request DTO | Slice name + "Request" | `data class CreateOrderRequest(...)` |
| Response DTO | Slice name + "Response" | `data class CreateOrderResponse(...)` |

## Route Registration

Routes registered directly in the composition root — no intermediate `Routing.kt`:

```kotlin
fun AppServer(db: Db = Db()) = App {
    val server = HttpServer(8080) {
        setupApplicationEnvironment()
        configureRoutes(db)
    }
    server.start()
    Teardown { server.stop(1000L, 1000L) }
}

private fun Application.configureRoutes(db: Db) {
    routing {
        getProductsRoute(db)

        // ── Order flow ──
        createOrderRoute(db)
        getOrderRoute(db)
        assignShippingRoute(db)
        payOrderRoute(db)
        cancelOrderRoute(db)
    }
}
```

**Key:** the app entry point is the **composition root** — where slices are wired with `Db`.
Each slice's route function handles its own internal wiring (Handler, GatherInput, ProduceOutput).

## Dependency Flow

```
                 ┌──────────────────┐
                 │    AppServer      │ ← composition root
                 └────────┬─────────┘
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ orders/  │  │ catalog/ │  │ payments/│  ← feature groups
      └────┬─────┘  └────┬─────┘  └────┬─────┘
           │              │              │
           ▼              ▼              ▼
      ┌─────────────────────────────────────┐
      │           common/                    │  ← shared pure code
      │  domain/ http/ infra/ app/           │
      └─────────────────────────────────────┘

Rules:
  ✅ Slice → common/          (allowed)
  ✅ Slice → DomainError       (allowed — shared within feature group)
  ❌ Slice → another slice    (forbidden)
  ❌ common/ → any slice      (forbidden)
```

## Migrating From Layered Architecture

### Step 1: Pick one feature
Don't refactor everything at once. Pick one feature (e.g., "create order").

### Step 2: Create the slice folder with 5 files
```
orders/createOrder/
├── CreateOrder.kt
├── Domain.kt
├── CreateOrderHandler.kt
├── GatherCreateOrderInput.kt
└── ProduceCreateOrderOutput.kt
```

### Step 3: Extract pure logic into Domain.kt
Move validation, calculations, branching into a pure `fun createOrder(input): Decision`.

### Step 4: Extract IO phases
GatherInput collects data, ProduceOutput persists + maps errors.

### Step 5: Wire in Route
Route function creates Handler with real deps.

### Step 6: Delete old layers
Remove the old controller, service, and repo methods for this feature.

### Step 7: Repeat per feature
Each feature migrates independently. Old and new coexist.

## Feature Group Boundaries

When to create a new feature group folder:

| Signal | Action |
|---|---|
| Different aggregate root | New folder: `orders/`, `catalog/` |
| Different bounded context | New folder: `billing/`, `shipping/` |
| > 8 slices in one group | Consider splitting by subdomain |
| Shared error vocabulary | Same group (e.g., all order slices share `DomainError.kt`) |
| No shared data at all | Definitely separate groups |
