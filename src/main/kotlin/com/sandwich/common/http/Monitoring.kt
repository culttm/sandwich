package com.sandwich.common.http

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*

fun Application.configureMonitoring() {
    install(CallLogging)
}
