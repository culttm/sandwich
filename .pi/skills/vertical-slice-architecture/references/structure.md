# Project Structure

How to organize a Kotlin/Ktor project with Vertical Slice Architecture.

## Canonical Structure

```
src/main/kotlin/com/sandwich/
├── apps/
│   └── SandwichHttpApi.kt              ← composition root + route registration
├── features/
│   ├── menu/
│   │   └── getMenu/
│   │       └── GetMenu.kt             ← query slice (single file)
│   └── orders/
│       ├── OrderError.kt              ← shared error vocabulary for all order slices
│       ├── createOrder/               ← command slice (5 files)
│       │   ├── CreateOrder.kt          ← HTTP DTOs + route (wiring + protocol)
│       │   ├── Domain.kt              ← Input + Decision + pure logic
│       │   ├── CreateOrderHandler.kt  ← orchestrator
│       │   ├── GatherCreateOrderInput.kt
│       │   └── ProduceCreateOrderOutput.kt
│       ├── setDelivery/               ← command slice
│       │   ├── SetDelivery.kt
│       │   ├── Domain.kt
│       │   ├── SetDeliveryHandler.kt
│       │   ├── GatherSetDeliveryInput.kt
│       │   └── ProduceSetDeliveryOutput.kt
│       ├── payOrder/                  ← command slice
│       │   └── ...
│       ├── dispatchOrder/             ← command slice
│       │   └── ...
│       ├── completeDelivery/          ← command slice
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
    │   └── Db.kt                      ← in-memory store
    └── app/
        └── App.kt                     ← lifecycle management
```

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Feature group folder | noun, plural, lowercase | `orders/`, `menu/` |
| Slice folder | camelCase verb+noun | `createOrder/`, `getOrder/` |
| Entry point file | PascalCase, matches folder | `CreateOrder.kt` |
| Domain file | Always `Domain.kt` | `Domain.kt` |
| Handler | `{SliceName}Handler.kt` | `CreateOrderHandler.kt` |
| GatherInput | `Gather{SliceName}Input.kt` | `GatherCreateOrderInput.kt` |
| ProduceOutput | `Produce{SliceName}Output.kt` | `ProduceCreateOrderOutput.kt` |
| Pure logic function | camelCase | `fun buildOrder(...)` |
| Sealed decision | PascalCase + "Decision" | `sealed interface CreateOrderDecision` |
| Input type | PascalCase + "Input" | `data class CreateOrderInput(...)` |
| Request DTO | Slice name + "Request" | `data class CreateOrderRequest(...)` |
| Response DTO | Slice name + "Response" | `data class CreateOrderResponse(...)` |

## Route Registration

Routes registered directly in `SandwichHttpApi.kt` — no intermediate `Routing.kt`:

```kotlin
fun SandwichHttpApi(db: Db = Db().apply { seed() }) = App {
    val server = HttpServer(8080) {
        setupApplicationEnvironment()
        configureRoutes(db)
    }
    server.start()
    Teardown { server.stop(1000L, 1000L) }
}

private fun Application.configureRoutes(db: Db) {
    routing {
        getMenuRoute(db)

        // ── Checkout flow ──
        createOrderRoute(db)
        getOrderRoute(db)
        setDeliveryRoute(db)
        payOrderRoute(db)

        // ── Fulfillment ──
        dispatchOrderRoute(db)
        completeDeliveryRoute(db)
        cancelOrderRoute(db)
    }
}
```

**Key:** `SandwichHttpApi.kt` is the **composition root** — where slices are wired with `Db`.
Each slice's route function handles its own internal wiring (Handler, GatherInput, ProduceOutput).

## Dependency Flow

```
                 ┌──────────────────┐
                 │ SandwichHttpApi   │ ← composition root
                 └────────┬─────────┘
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ orders/  │  │  menu/   │  │ payments/│  ← feature groups
      └────┬─────┘  └────┬─────┘  └────┬─────┘
           │              │              │
           ▼              ▼              ▼
      ┌─────────────────────────────────────┐
      │           common/                    │  ← shared pure code
      │  domain/ http/ infra/ app/           │
      └─────────────────────────────────────┘

Rules:
  ✅ Slice → common/         (allowed)
  ✅ Slice → OrderError      (allowed — shared within feature group)
  ❌ Slice → another slice   (forbidden)
  ❌ common/ → any slice     (forbidden)
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
Move validation, calculations, branching into a pure `fun buildOrder(input): Decision`.

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
| Different aggregate root | New folder: `orders/`, `menu/` |
| Different bounded context | New folder: `billing/`, `shipping/` |
| > 8 slices in one group | Consider splitting by subdomain |
| Shared error vocabulary | Same group (e.g., all order slices share `OrderError.kt`) |
| No shared data at all | Definitely separate groups |
