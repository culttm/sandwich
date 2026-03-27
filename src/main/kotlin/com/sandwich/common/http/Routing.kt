package com.sandwich.common.http

import com.sandwich.common.infra.Db
import com.sandwich.features.menu.getMenu.GetMenu
import com.sandwich.features.orders.cancelOrder.CancelOrder
import com.sandwich.features.orders.completeDelivery.CompleteDelivery
import com.sandwich.features.orders.createOrder.CreateOrder
import com.sandwich.features.orders.createOrder.CreateOrderRequest
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
    val createOrder = CreateOrder(db)
    val getOrder = GetOrder(db)
    val setDelivery = SetDelivery(db)
    val payOrder = PayOrder(db)
    val dispatchOrder = DispatchOrder(db)
    val completeDelivery = CompleteDelivery(db)
    val cancelOrder = CancelOrder(db)

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/menu") {
            call.respond(getMenu())
        }

        // ── Checkout flow ──

        post("/orders") {
            val request = call.receive<CreateOrderRequest>()
            call.respond(HttpStatusCode.Created, createOrder(request))
        }

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

        post("/orders/{id}/cancel") {
            val id = call.parameters["id"]!!
            call.respond(cancelOrder(id))
        }
    }
}
