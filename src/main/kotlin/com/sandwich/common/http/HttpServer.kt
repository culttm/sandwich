package com.sandwich.common.http

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

class HttpServer(port: Int, configure: Application.() -> Unit = {}) {
    private val server = embeddedServer(Netty, port = port) {
        configure()
    }

    fun start(wait: Boolean = false) {
        server.start(wait = wait)
    }

    fun stop(gracePeriod: Long = 1000L, timeout: Long = 1000L) {
        server.stop(gracePeriod, timeout)
    }
}