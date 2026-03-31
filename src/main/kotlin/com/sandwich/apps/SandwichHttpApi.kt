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
import com.sandwich.common.infra.StockEntry
import com.sandwich.features.CatalogItem
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.Application
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun SandwichHttpApi(
    db: Db,
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

suspend fun Db.seed() {
    saveCatalogItems(
        CatalogItem("classic-club",   "Classic Club",   120, "sandwich"),
        CatalogItem("turkey-avocado", "Turkey Avocado", 145, "sandwich"),
        CatalogItem("veggie-delight", "Veggie Delight",  99, "sandwich"),
        CatalogItem("blt",            "BLT",            110, "sandwich"),
        CatalogItem("extra-cheese",   "Сір додатковий",  25, "extra"),
        CatalogItem("jalapenos",      "Халапеньо",       15, "extra"),
        CatalogItem("bacon",          "Бекон",           35, "extra"),
        CatalogItem("avocado",        "Авокадо",         30, "extra"),
        CatalogItem("extra-sauce",    "Соус додатковий", 10, "extra"),
    )
    saveStockEntries(
        StockEntry("classic-club",   50),
        StockEntry("turkey-avocado", 30),
        StockEntry("veggie-delight", 40),
        StockEntry("blt",            35),
    )
}
