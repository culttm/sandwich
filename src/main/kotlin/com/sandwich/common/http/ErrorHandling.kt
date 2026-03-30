package com.sandwich.common.http

import com.sandwich.features.orders.OrderException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<OrderException> { call, e ->
            call.respond(
                e.error.code.status,
                mapOf("code" to e.error.code.name, "message" to e.error.message)
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Internal error")))
        }
    }
}
