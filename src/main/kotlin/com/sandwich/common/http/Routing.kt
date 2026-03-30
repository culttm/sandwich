package com.sandwich.common.http

import com.sandwich.common.infra.Db
import com.sandwich.features.menu.getMenu.GetMenu
import com.sandwich.features.orders.cancelOrder.cancelOrderRoute
import com.sandwich.features.orders.completeDelivery.CompleteDelivery
import com.sandwich.features.orders.createOrder.createOrderRoute
import com.sandwich.features.orders.dispatchOrder.DispatchOrder
import com.sandwich.features.orders.getOrder.GetOrder
import com.sandwich.features.orders.payOrder.PayOrder
import com.sandwich.features.orders.payOrder.PayOrderRequest
import com.sandwich.features.orders.setDelivery.SetDelivery
import com.sandwich.features.orders.setDelivery.SetDeliveryRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(db: Db) {
    val getMenu = GetMenu(db)
    val getOrder = GetOrder(db)
    val setDelivery = SetDelivery(db)
    val payOrder = PayOrder(db)
    val dispatchOrder = DispatchOrder(db)
    val completeDelivery = CompleteDelivery(db)
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

        post("/orders/{id}/delivery") {
            val id = call.parameters["id"]!!
            val request = call.receive<SetDeliveryRequest>()
            call.respond(setDelivery(id, request))
        }

        post("/orders/{id}/pay") {
            val id = call.parameters["id"]!!
            val request = call.receive<PayOrderRequest>()
            call.respond(payOrder(id, request))
        }

        // ── Fulfillment ──

        post("/orders/{id}/dispatch") {
            val id = call.parameters["id"]!!
            call.respond(dispatchOrder(id))
        }

        post("/orders/{id}/complete") {
            val id = call.parameters["id"]!!
            call.respond(completeDelivery(id))
        }

        cancelOrderRoute(db)
    }
}
