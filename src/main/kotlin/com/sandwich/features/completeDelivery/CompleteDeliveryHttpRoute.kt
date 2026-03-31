package com.sandwich.features.completeDelivery

import com.sandwich.common.infra.Db
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

fun Route.completeDeliveryRoute(db: Db) = completeDeliveryRoute(
    CompleteDeliveryHandler(
        gatherInput = GatherCompleteDeliveryInput(
            readOrder = { id -> db.orders[id] }
        ),
        decide = ::completeDelivery,
        produceOutput = ProduceCompleteDeliveryOutput(
            storeOrder = { order -> db.orders[order.id] = order }
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
