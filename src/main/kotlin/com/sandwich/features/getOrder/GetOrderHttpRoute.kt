package com.sandwich.features.getOrder

import com.sandwich.features.Order
import com.sandwich.common.infra.Db
import com.sandwich.features.OrderErrorCode.*
import com.sandwich.features.orderError
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// ══════════════════════════════════════════════════════════════
//  Slice entry point: read-only query (no WRITE phase)
// ══════════════════════════════════════════════════════════════

// ── Route (wiring) ──

fun Route.getOrderRoute(db: Db) = getOrderRoute(
    handler = { orderId -> db.orders[orderId] ?: orderError(ORDER_NOT_FOUND, "Замовлення не знайдено") }
)

// ── Route (HTTP) ──

fun Route.getOrderRoute(handler: suspend (String) -> Order) {
    get("/orders/{id}") {
        val id = call.parameters["id"]!!
        call.respond(HttpStatusCode.OK, handler(id))
    }
}
