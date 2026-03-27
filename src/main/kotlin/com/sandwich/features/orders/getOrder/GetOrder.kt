package com.sandwich.features.orders.getOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.infra.OrderStore

// ══════════════════════════════════════════════════
//  Level 1: Direct Query
//  Просте читання — без бізнес-логіки, без sandwich.
// ══════════════════════════════════════════════════

fun GetOrder(orderStore: OrderStore): suspend (String) -> Order? = { orderId ->
    orderStore.findById(orderId)
}
