package com.sandwich.features.orders.getOrder

import com.sandwich.features.orders.Order
import com.sandwich.common.infra.Db
import com.sandwich.features.orders.OrderErrorCode.*
import com.sandwich.features.orders.orderError
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
