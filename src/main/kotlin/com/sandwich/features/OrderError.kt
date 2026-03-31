package com.sandwich.features

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

// ══════════════════════════════════════════════════════════════
//  Shared error vocabulary для всіх order-слайсів
//  Кожен код визначає HTTP статус — StatusPages перетворює
//  OrderException у відповідну HTTP-відповідь.
// ══════════════════════════════════════════════════════════════

enum class OrderErrorCode(val status: HttpStatusCode) {
    // createOrder
    BLANK_NAME(HttpStatusCode.BadRequest),
    EMPTY_ORDER(HttpStatusCode.BadRequest),
    TOO_MANY_ITEMS(HttpStatusCode.BadRequest),
    UNKNOWN_SANDWICH(HttpStatusCode.BadRequest),
    UNKNOWN_EXTRAS(HttpStatusCode.BadRequest),
    TOO_MANY_EXTRAS(HttpStatusCode.BadRequest),

    // shared
    ORDER_NOT_FOUND(HttpStatusCode.NotFound),
    WRONG_STATUS(HttpStatusCode.Conflict),

    // setDelivery
    BLANK_ADDRESS(HttpStatusCode.BadRequest),
    BLANK_PHONE(HttpStatusCode.BadRequest),

    // payOrder
    OUT_OF_STOCK(HttpStatusCode.Conflict),

    // cancelOrder
    ALREADY_CANCELLED(HttpStatusCode.Conflict),
    TOO_LATE(HttpStatusCode.Conflict),
    CANCEL_WINDOW_EXPIRED(HttpStatusCode.Conflict),
}

data class OrderError(
    val code: OrderErrorCode,
    val message: String
)

class OrderException(val error: OrderError) : Exception(error.message)

fun orderError(code: OrderErrorCode, message: String): Nothing =
    throw OrderException(OrderError(code, message))

fun StatusPagesConfig.orderErrorHandling() {
    exception<OrderException> { call, e ->
        call.respond(
            e.error.code.status,
            mapOf("code" to e.error.code.name, "message" to e.error.message)
        )
    }
}
