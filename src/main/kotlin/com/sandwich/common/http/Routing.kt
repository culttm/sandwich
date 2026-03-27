package com.sandwich.common.http

import com.sandwich.common.infra.Db
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

fun Application.configureRouting(db: Db) {
    val getMenu = GetMenu(db)
    val placeOrder = PlaceOrder(db)
    val getOrder = GetOrder(db)
    val cancelOrder = CancelOrder(db)

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/menu") {
            call.respond(getMenu())
        }

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
