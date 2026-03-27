package com.sandwich.features.orders.getOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.infra.Db

// ── Level 1: Direct Query ──

fun GetOrder(db: Db): suspend (String) -> Order? = { orderId ->
    db.orders[orderId]
}
