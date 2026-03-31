package com.sandwich.features.createOrder

import com.sandwich.common.database.bson.CatalogItemBson
import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.collection.catalog.allByCategory
import com.sandwich.common.database.collection.order.saveOrder
import com.mongodb.kotlin.client.coroutine.MongoCollection
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

fun Route.createOrderRoute(
    catalogItems: MongoCollection<CatalogItemBson>,
    orders: MongoCollection<OrderBson>
) = createOrderRoute(
    CreateOrderHandler(
        gatherInput = GatherCreateOrderInput(
            readMenu = { catalogItems.allByCategory("sandwich") },
            readExtras = { catalogItems.allByCategory("extra") },
            generateId = { UUID.randomUUID().toString() },
            now = Instant::now
        ),
        decide = ::createOrder,
        produceOutput = ProduceCreateOrderOutput(
            storeOrder = { order -> orders.saveOrder(order) }
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
