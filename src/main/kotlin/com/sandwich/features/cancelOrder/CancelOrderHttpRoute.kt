package com.sandwich.features.cancelOrder

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.bson.StockEntryBson
import com.sandwich.common.database.collection.order.findOrderById
import com.sandwich.common.database.collection.order.saveOrder
import com.sandwich.common.database.collection.stock.adjustStock
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Slice entry point: HTTP DTOs + route + wiring
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class CancelResponse(val orderId: String, val status: String, val refund: Boolean)

// ── Route (wiring) ──

fun Route.cancelOrderRoute(
    orders: MongoCollection<OrderBson>,
    stock: MongoCollection<StockEntryBson>
) = cancelOrderRoute(
    CancelOrderHandler(
        gatherInput = GatherCancelOrderInput(
            readOrder = { id -> orders.findOrderById(id) },
            now = Instant::now
        ),
        decide = ::cancelOrder,
        produceOutput = ProduceCancelOrderOutput(
            storeOrder = { order -> orders.saveOrder(order) },
            releaseStock = { reductions ->
                reductions.forEach { (id, qty) -> stock.adjustStock(id, qty) }
            }
        )
    )
)

// ── Route (HTTP) ──

fun Route.cancelOrderRoute(handler: suspend (String) -> CancelResponse) {
    post("/orders/{id}/cancel") {
        val id = call.parameters["id"]!!
        call.respond(HttpStatusCode.OK, handler(id))
    }
}
