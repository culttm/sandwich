package com.sandwich.features.cancelOrder

import com.sandwich.common.infra.Db
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

fun Route.cancelOrderRoute(db: Db) = cancelOrderRoute(
    CancelOrderHandler(
        gatherInput = GatherCancelOrderInput(
            readOrder = { id -> db.findOrder(id) },
            now = Instant::now
        ),
        decide = ::cancelOrder,
        produceOutput = ProduceCancelOrderOutput(
            storeOrder = { order -> db.saveOrder(order) },
            releaseStock = { stock ->
                stock.forEach { (id, qty) -> db.adjustStock(id, qty) }
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
