package com.sandwich.features.orders.payOrder

import com.sandwich.common.domain.PaymentMethod
import com.sandwich.common.infra.Db
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Slice entry point: HTTP DTOs + route + wiring
// ══════════════════════════════════════════════════════════════

// ── HTTP DTOs ──

@Serializable
data class PayOrderRequest(val method: PaymentMethod)

@Serializable
data class PayOrderResponse(
    val orderId: String,
    val status: String,
    val paymentMethod: PaymentMethod
)

// ── Route (wiring) ──

fun Route.payOrderRoute(db: Db) = payOrderRoute(
    PayOrderHandler(
        gatherInput = GatherPayOrderInput(
            readOrder = { id -> db.orders[id] },
            readStock = { db.stock.toMap() },
            now = Instant::now,
            generateTransactionId = { UUID.randomUUID().toString() }
        ),
        decide = ::decidePayment,
        produceOutput = ProducePayOrderOutput(
            storeOrder = { order -> db.orders[order.id] = order },
            reduceStock = { reductions ->
                reductions.forEach { (id, qty) ->
                    db.stock.compute(id) { _, current -> (current ?: 0) - qty }
                }
            }
        )
    )
)

// ── Route (HTTP) ──

fun Route.payOrderRoute(handler: suspend (String, PayOrderRequest) -> PayOrderResponse) {
    post("/orders/{id}/pay") {
        val id = call.parameters["id"]!!
        val request = call.receive<PayOrderRequest>()
        call.respond(HttpStatusCode.OK, handler(id, request))
    }
}
