package com.sandwich.features.dispatchOrder

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.collection.order.findOrderById
import com.sandwich.common.database.collection.order.saveOrder
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Slice entry point: HTTP DTOs + route + wiring
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class DispatchResponse(val orderId: String, val status: String)

// ── Route (wiring) ──

fun Route.dispatchOrderRoute(orders: MongoCollection<OrderBson>) = dispatchOrderRoute(
    DispatchOrderHandler(
        gatherInput = GatherDispatchOrderInput(
            readOrder = { id -> orders.findOrderById(id) }
        ),
        decide = ::dispatchOrder,
        produceOutput = ProduceDispatchOrderOutput(
            storeOrder = { order -> orders.saveOrder(order) }
        )
    )
)

// ── Route (HTTP) ──

fun Route.dispatchOrderRoute(handler: suspend (String) -> DispatchResponse) {
    post("/orders/{id}/dispatch") {
        val id = call.parameters["id"]!!
        call.respond(HttpStatusCode.OK, handler(id))
    }
}
