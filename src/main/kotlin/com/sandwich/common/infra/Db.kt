package com.sandwich.common.infra

import com.sandwich.features.menu.ExtraItem
import com.sandwich.features.menu.MenuItem
import com.sandwich.features.orders.Order
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory "з'єднання з БД".
 * В реальному сервісі — DataSource / Exposed Database / jOOQ DSLContext.
 * Кожний слайс отримує тільки Db і робить свої запити сам.
 */
class Db {
    val sandwiches = ConcurrentHashMap<String, MenuItem>()
    val extras = ConcurrentHashMap<String, ExtraItem>()
    val orders = ConcurrentHashMap<String, Order>()
    val stock = ConcurrentHashMap<String, Int>()   // sandwichId → скільки можемо зробити
}

fun Db.seed() {
    sandwiches.putAll(
        mapOf(
            "classic-club"   to MenuItem("classic-club",   "Classic Club",   120),
            "turkey-avocado" to MenuItem("turkey-avocado", "Turkey Avocado", 145),
            "veggie-delight" to MenuItem("veggie-delight", "Veggie Delight",  99),
            "blt"            to MenuItem("blt",            "BLT",            110),
        )
    )
    extras.putAll(
        mapOf(
            "extra-cheese" to ExtraItem("extra-cheese", "Сир додатковий", 25),
            "jalapenos"    to ExtraItem("jalapenos",    "Халапеньо",       15),
            "bacon"        to ExtraItem("bacon",        "Бекон",           35),
            "avocado"      to ExtraItem("avocado",      "Авокадо",         30),
            "extra-sauce"  to ExtraItem("extra-sauce",  "Соус додатковий", 10),
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
