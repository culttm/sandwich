package com.sandwich.features.orders

import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun StatusPagesConfig.orderErrorHandling() {
    exception<OrderException> { call, e ->
        call.respond(
            e.error.code.status,
            mapOf("code" to e.error.code.name, "message" to e.error.message)
        )
    }
}
