package com.sandwich.apps

import com.sandwich.common.app.App
import com.sandwich.common.app.Teardown
import com.sandwich.common.http.HttpServer
import com.sandwich.common.http.configureErrorHandling
import com.sandwich.features.orderErrorHandling
import com.sandwich.common.http.configureMonitoring
import com.sandwich.common.http.configureSerialization
import com.sandwich.features.getMenu.getMenuRoute
import com.sandwich.features.cancelOrder.cancelOrderRoute
import com.sandwich.features.completeDelivery.completeDeliveryRoute
import com.sandwich.features.createOrder.createOrderRoute
import com.sandwich.features.dispatchOrder.dispatchOrderRoute
import com.sandwich.features.getOrder.getOrderRoute
import com.sandwich.features.payOrder.payOrderRoute
import com.sandwich.features.setDelivery.setDeliveryRoute
import com.sandwich.common.infra.Db
import com.sandwich.features.CatalogItem
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
    configureErrorHandling { orderErrorHandling() }
    configureMonitoring()
    configureHealthRoutes()
}

fun Db.seed() {
    sandwiches.putAll(
        mapOf(
            "classic-club"   to CatalogItem("classic-club",   "Classic Club",   120),
            "turkey-avocado" to CatalogItem("turkey-avocado", "Turkey Avocado", 145),
            "veggie-delight" to CatalogItem("veggie-delight", "Veggie Delight",  99),
            "blt"            to CatalogItem("blt",            "BLT",            110),
        )
    )
    extras.putAll(
        mapOf(
            "extra-cheese" to CatalogItem("extra-cheese", "Сир додатковий", 25),
            "jalapenos"    to CatalogItem("jalapenos",    "Халапеньо",       15),
            "bacon"        to CatalogItem("bacon",        "Бекон",           35),
            "avocado"      to CatalogItem("avocado",      "Авокадо",         30),
            "extra-sauce"  to CatalogItem("extra-sauce",  "Соус додатковий", 10),
        )
    )
    stock.putAll(
        mapOf(
            "classic-club"   to 50,
            "turkey-avocado" to 30,
            "veggie-delight" to 40,
            "blt"            to 35,
        )
    )
}
