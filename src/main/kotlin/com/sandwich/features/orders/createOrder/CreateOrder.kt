package com.sandwich.features.orders.createOrder

import com.sandwich.common.infra.Db
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Slice entry point: HTTP DTOs + route + wiring
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class CreateOrderRequest(
    val customerName: String,
    val items: List<OrderItemRequest>
)

@Serializable
data class CreateOrderResponse(val orderId: String, val total: Int)

// ── Route (wiring) ──

fun Route.createOrderRoute(db: Db) = createOrderRoute(
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readMenu = { db.sandwiches.toMap() },
            readExtras = { db.extras.toMap() },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::createOrder,
        produceOutput = ProduceCreateOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order }
        )
    )
)

// ── Route (HTTP) ──

fun Route.createOrderRoute(handler: suspend (CreateOrderRequest) -> CreateOrderResponse) {
    post("/orders") {
        val request = call.receive<CreateOrderRequest>()
        call.respond(HttpStatusCode.Created, handler(request))
    }
}
