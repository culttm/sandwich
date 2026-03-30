package com.sandwich.features.orders.createOrder

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.MenuItem
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCreateOrderInput(
    readMenu: () -> Map<String, MenuItem>,
    readExtras: () -> Map<String, ExtraItem>,
    generateId: () -> String,
    now: () -> Instant
): (CreateOrderRequest) -> CreateOrderInput = { request ->
    CreateOrderInput(
        orderId = generateId(),
        customerName = request.customerName,
        items = request.items,
        menu = readMenu(),
        extras = readExtras(),
        now = now()
    )
}
