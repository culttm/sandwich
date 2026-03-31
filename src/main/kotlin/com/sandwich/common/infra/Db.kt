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
