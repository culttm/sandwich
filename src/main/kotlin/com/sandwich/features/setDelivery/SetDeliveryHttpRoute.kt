package com.sandwich.features.setDelivery

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.collection.order.findOrderById
import com.sandwich.common.database.collection.order.saveOrder
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════
//  Slice entry point: HTTP DTOs + route + wiring
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class SetDeliveryRequest(
    val address: String,
    val phone: String,
    val deliveryTime: String? = null   // null = "якнайшвидше"
)

@Serializable
data class SetDeliveryResponse(
    val orderId: String,
    val deliveryFee: Int,
    val total: Int
)

// ── Route (wiring) ──

fun Route.setDeliveryRoute(orders: MongoCollection<OrderBson>) = setDeliveryRoute(
    SetDeliveryHandler(
        gatherInput = GatherSetDeliveryInput(
            readOrder = { id -> orders.findOrderById(id) }
        ),
        decide = ::setDelivery,
        produceOutput = ProduceSetDeliveryOutput(
            storeOrder = { order -> orders.saveOrder(order) }
        )
    )
)

// ── Route (HTTP) ──

fun Route.setDeliveryRoute(handler: suspend (String, SetDeliveryRequest) -> SetDeliveryResponse) {
    post("/orders/{id}/delivery") {
        val id = call.parameters["id"]!!
        val request = call.receive<SetDeliveryRequest>()
        call.respond(HttpStatusCode.OK, handler(id, request))
    }
}
