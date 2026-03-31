package com.sandwich.features.getMenu

import com.sandwich.common.database.bson.CatalogItemBson
import com.sandwich.common.database.collection.catalog.allByCategory
import com.sandwich.features.Menu
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// ══════════════════════════════════════════════════════════════
//  Slice entry point: read-only query (no WRITE phase)
// ══════════════════════════════════════════════════════════════

// ── Route (wiring) ──

fun Route.getMenuRoute(catalogItems: MongoCollection<CatalogItemBson>) = getMenuRoute(
    handler = {
        Menu(
            sandwiches = catalogItems.allByCategory("sandwich").values.toList(),
            extras = catalogItems.allByCategory("extra").values.toList()
        )
    }
)

// ── Route (HTTP) ──

fun Route.getMenuRoute(handler: suspend () -> Menu) {
    get("/menu") {
        call.respond(HttpStatusCode.OK, handler())
    }
}
