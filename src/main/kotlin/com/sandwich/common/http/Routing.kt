package com.sandwich.common.http

import com.sandwich.common.infra.Db
import com.sandwich.features.menu.getMenu.getMenuRoute
import com.sandwich.features.orders.cancelOrder.cancelOrderRoute
import com.sandwich.features.orders.completeDelivery.completeDeliveryRoute
import com.sandwich.features.orders.createOrder.createOrderRoute
import com.sandwich.features.orders.dispatchOrder.dispatchOrderRoute
import com.sandwich.features.orders.getOrder.getOrderRoute
import com.sandwich.features.orders.payOrder.payOrderRoute
import com.sandwich.features.orders.setDelivery.setDeliveryRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(db: Db) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        getMenuRoute(db)

        // ── Checkout flow ──

        createOrderRoute(db)
        getOrderRoute(db)
        setDeliveryRoute(db)
        payOrderRoute(db)

        // ── Fulfillment ──

        dispatchOrderRoute(db)
        completeDeliveryRoute(db)
        cancelOrderRoute(db)
    }
}
