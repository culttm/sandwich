package com.sandwich.common.http

import com.sandwich.common.infra.Db
import com.sandwich.features.menu.getMenu.GetMenu
import com.sandwich.features.orders.cancelOrder.cancelOrderRoute
import com.sandwich.features.orders.completeDelivery.completeDeliveryRoute
import com.sandwich.features.orders.createOrder.createOrderRoute
import com.sandwich.features.orders.dispatchOrder.dispatchOrderRoute
import com.sandwich.features.orders.getOrder.GetOrder
import com.sandwich.features.orders.payOrder.payOrderRoute
import com.sandwich.features.orders.setDelivery.setDeliveryRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(db: Db) {
    val getMenu = GetMenu(db)
    val getOrder = GetOrder(db)

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/menu") {
            call.respond(getMenu())
        }

        // ── Checkout flow ──

        createOrderRoute(db)

        get("/orders/{id}") {
            val id = call.parameters["id"]!!
            val order = getOrder(id)
            if (order != null) call.respond(order)
            else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Замовлення не знайдено"))
        }

        setDeliveryRoute(db)
        payOrderRoute(db)

        // ── Fulfillment ──

        dispatchOrderRoute(db)
        completeDeliveryRoute(db)
        cancelOrderRoute(db)
    }
}
