package com.sandwich.features.orders.createOrder

import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Composition root: HTTP DTOs + wiring залежностей
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class CreateOrderRequest(
    val customerName: String,
    val items: List<OrderItemRequest>
)

@Serializable
data class CreateOrderResponse(val orderId: String, val total: Int)

// ── Wiring ──

fun CreateOrder(db: Db): suspend (CreateOrderRequest) -> CreateOrderResponse =
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readMenu = { db.sandwiches.toMap() },
            readExtras = { db.extras.toMap() },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::buildOrder,
        produceOutput = ProduceCreateOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
