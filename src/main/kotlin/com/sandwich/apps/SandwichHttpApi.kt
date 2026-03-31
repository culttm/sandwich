package com.sandwich.apps

import com.sandwich.common.app.App
import com.sandwich.common.app.Teardown
import com.sandwich.common.database.bson.CatalogItemBson
import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.bson.StockEntryBson
import com.sandwich.common.database.bson.toBson
import com.sandwich.common.database.collection.catalog.saveCatalogItems
import com.sandwich.common.database.collection.stock.saveStockEntries
import com.sandwich.common.http.HttpServer
import com.sandwich.common.http.configureErrorHandling
import com.sandwich.features.orderErrorHandling
import com.sandwich.common.http.configureMonitoring
import com.sandwich.common.http.configureSerialization
import com.sandwich.features.CatalogItem
import com.sandwich.features.getMenu.getMenuRoute
import com.sandwich.features.cancelOrder.cancelOrderRoute
import com.sandwich.features.completeDelivery.completeDeliveryRoute
import com.sandwich.features.createOrder.createOrderRoute
import com.sandwich.features.dispatchOrder.dispatchOrderRoute
import com.sandwich.features.getOrder.getOrderRoute
import com.sandwich.features.payOrder.payOrderRoute
import com.sandwich.features.setDelivery.setDeliveryRoute
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.Application
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun SandwichHttpApi(
    database: MongoDatabase,
    logger: Logger = LoggerFactory.getLogger("SandwichHttpApi")
) = App {
    logger.info("Starting SandwichHttpApi")

    val catalogItems = database.getCollection<CatalogItemBson>("catalog_items")
    val orders = database.getCollection<OrderBson>("orders")
    val stock = database.getCollection<StockEntryBson>("stock")

    val server = HttpServer(8080) {
        setupApplicationEnvironment()
        configureRoutes(catalogItems, orders, stock)
    }
    server.start()

    Teardown {
        logger.info("Stopping SandwichHttpApi")
        server.stop(1000L, 1000L)
        logger.info("Stopped SandwichHttpApi")
    }
}

private fun Application.configureRoutes(
    catalogItems: MongoCollection<CatalogItemBson>,
    orders: MongoCollection<OrderBson>,
    stock: MongoCollection<StockEntryBson>
) {
    routing {
        getMenuRoute(catalogItems)

        // ── Checkout flow ──
        createOrderRoute(catalogItems, orders)
        getOrderRoute(orders)
        setDeliveryRoute(orders)
        payOrderRoute(orders, stock)

        // ── Fulfillment ──
        dispatchOrderRoute(orders)
        completeDeliveryRoute(orders)
        cancelOrderRoute(orders, stock)
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

suspend fun MongoDatabase.seed() {
    val catalogItems = getCollection<CatalogItemBson>("catalog_items")
    val stock = getCollection<StockEntryBson>("stock")

    catalogItems.saveCatalogItems(
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
    stock.saveStockEntries(
        "classic-club" to 50,
        "turkey-avocado" to 30,
        "veggie-delight" to 40,
        "blt" to 35,
    )
}
