package com.sandwich.features.dispatchOrder

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
data class DispatchResponse(val orderId: String, val status: String)

// ── Route (wiring) ──

fun Route.dispatchOrderRoute(db: Db) = dispatchOrderRoute(
    DispatchOrderHandler(
        gatherInput = GatherDispatchOrderInput(
            readOrder = { id -> db.orders[id] }
        ),
        decide = ::dispatchOrder,
        produceOutput = ProduceDispatchOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order }
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
