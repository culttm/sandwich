package com.sandwich.features.orders.getMenu

import com.sandwich.features.orders.Menu
import com.sandwich.common.infra.Db
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// ══════════════════════════════════════════════════════════════
//  Slice entry point: read-only query (no WRITE phase)
// ══════════════════════════════════════════════════════════════

// ── Route (wiring) ──

fun Route.getMenuRoute(db: Db) = getMenuRoute(
    handler = { Menu(sandwiches = db.sandwiches.values.toList(), extras = db.extras.values.toList()) }
)

// ── Route (HTTP) ──

fun Route.getMenuRoute(handler: suspend () -> Menu) {
    get("/menu") {
        call.respond(HttpStatusCode.OK, handler())
    }
}
