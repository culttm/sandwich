package com.sandwich.apps

import com.sandwich.common.app.App
import com.sandwich.common.app.Teardown
import com.sandwich.common.http.HttpServer
import com.sandwich.common.http.configureErrorHandling
import com.sandwich.common.http.configureMonitoring
import com.sandwich.common.http.configureSerialization
import com.sandwich.features.menu.getMenu.getMenuRoute
import com.sandwich.features.orders.cancelOrder.cancelOrderRoute
import com.sandwich.features.orders.completeDelivery.completeDeliveryRoute
import com.sandwich.features.orders.createOrder.createOrderRoute
import com.sandwich.features.orders.dispatchOrder.dispatchOrderRoute
import com.sandwich.features.orders.getOrder.getOrderRoute
import com.sandwich.features.orders.payOrder.payOrderRoute
import com.sandwich.features.orders.setDelivery.setDeliveryRoute
import com.sandwich.common.infra.Db
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.sandwich.common.infra.seed
import io.ktor.server.application.Application
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun SandwichHttpApi(
    db: Db = Db().apply { seed() },
    logger: Logger = LoggerFactory.getLogger("SandwichHttpApi")
) = App {
    logger.info("Starting SandwichHttpApi")

    val server = HttpServer(8080) {
        setupApplicationEnvironment()
        configureRoutes(db)
    }
    server.start()

    Teardown {
        logger.info("Stopping SandwichHttpApi")
        server.stop(1000L, 1000L)
        logger.info("Stopped SandwichHttpApi")
    }
}

private fun Application.configureRoutes(db: Db) {
    routing {
        getMenuRoute(db)

        // ── Checkout flow ──
        createOrderRoute(db)
        getOrderRoute(db)
        setDeliveryRoute(db)
        payOrderRoute(db)

        // ── Fulfillment ──
        dispatchOrderRoute(db)
        completeDeliveryRoute(db)
        cancelOrderRoute(db)
    }
}

private fun Application.configureHealthRoutes() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}

private fun Application.setupApplicationEnvironment() {
    configureSerialization()
    configureErrorHandling()
    configureMonitoring()
    configureHealthRoutes()
}
