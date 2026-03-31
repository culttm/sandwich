package com.sandwich.common.infra

import com.sandwich.features.CatalogItem
import com.sandwich.features.Order
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory "з'єднання з БД".
 * В реальному сервісі — DataSource / Exposed Database / jOOQ DSLContext.
 * Кожний слайс отримує тільки Db і робить свої запити сам.
 */
class Db {
    val sandwiches = ConcurrentHashMap<String, CatalogItem>()
    val extras = ConcurrentHashMap<String, CatalogItem>()
    val orders = ConcurrentHashMap<String, Order>()
    val stock = ConcurrentHashMap<String, Int>()   // sandwichId → скільки можемо зробити
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
