package com.sandwich.features.completeDelivery

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
data class CompleteDeliveryResponse(val orderId: String, val status: String)

// ── Route (wiring) ──

fun Route.completeDeliveryRoute(orders: MongoCollection<OrderBson>) = completeDeliveryRoute(
    CompleteDeliveryHandler(
        gatherInput = GatherCompleteDeliveryInput(
            readOrder = { id -> orders.findOrderById(id) }
        ),
        decide = ::completeDelivery,
        produceOutput = ProduceCompleteDeliveryOutput(
            storeOrder = { order -> orders.saveOrder(order) }
        )
    )
)

// ── Route (HTTP) ──

fun Route.completeDeliveryRoute(handler: suspend (String) -> CompleteDeliveryResponse) {
    post("/orders/{id}/complete") {
        val id = call.parameters["id"]!!
        call.respond(HttpStatusCode.OK, handler(id))
    }
}
