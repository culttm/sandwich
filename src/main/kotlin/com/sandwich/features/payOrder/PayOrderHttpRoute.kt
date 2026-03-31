package com.sandwich.features.payOrder

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.bson.StockEntryBson
import com.sandwich.common.database.collection.order.findOrderById
import com.sandwich.common.database.collection.order.saveOrder
import com.sandwich.common.database.collection.stock.adjustStock
import com.sandwich.common.database.collection.stock.allStock
import com.sandwich.features.PaymentMethod
import com.mongodb.kotlin.client.coroutine.MongoCollection
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

fun Route.payOrderRoute(
    orders: MongoCollection<OrderBson>,
    stock: MongoCollection<StockEntryBson>
) = payOrderRoute(
    PayOrderHandler(
        gatherInput = GatherPayOrderInput(
            readOrder = { id -> orders.findOrderById(id) },
            readStock = { stock.allStock() },
            now = Instant::now,
            generateTransactionId = { UUID.randomUUID().toString() }
        ),
        decide = ::payOrder,
        produceOutput = ProducePayOrderOutput(
            storeOrder = { order -> orders.saveOrder(order) },
            reduceStock = { reductions ->
                reductions.forEach { (id, qty) -> stock.adjustStock(id, -qty) }
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
