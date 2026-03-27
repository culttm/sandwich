package com.sandwich.common.infra

import com.sandwich.common.domain.Order
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory сховище замовлень. В реальному сервісі — PostgreSQL.
 * Impure: мутабельний стан + suspend (в реальності).
 */
class OrderStore {
    private val orders = ConcurrentHashMap<String, Order>()

    suspend fun save(order: Order) {
        orders[order.id] = order
    }

    suspend fun findById(id: String): Order? =
        orders[id]

    suspend fun update(order: Order) {
        orders[order.id] = order
    }
}
