package com.sandwich.common.http

import com.sandwich.common.infra.MenuStore
import com.sandwich.common.infra.OrderStore
import com.sandwich.features.menu.getMenu.GetMenu
import com.sandwich.features.orders.cancelOrder.CancelOrder
import com.sandwich.features.orders.getOrder.GetOrder
import com.sandwich.features.orders.placeOrder.PlaceOrder
import com.sandwich.features.orders.placeOrder.PlaceOrderRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(menuStore: MenuStore, orderStore: OrderStore) {
    // ── Wire slices (composition root) ──
    val getMenu = GetMenu(menuStore)
    val placeOrder = PlaceOrder(menuStore, orderStore)
    val getOrder = GetOrder(orderStore)
    val cancelOrder = CancelOrder(orderStore)

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // ── Menu ──
        get("/menu") {
            call.respond(getMenu())
        }

        // ── Orders ──
        post("/orders") {
            val request = call.receive<PlaceOrderRequest>()
            call.respond(HttpStatusCode.Created, placeOrder(request))
        }

        get("/orders/{id}") {
            val id = call.parameters["id"]!!
            val order = getOrder(id)
            if (order != null) call.respond(order)
            else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Замовлення не знайдено"))
        }

        post("/orders/{id}/cancel") {
            val id = call.parameters["id"]!!
            call.respond(cancelOrder(id))
        }
    }
}
