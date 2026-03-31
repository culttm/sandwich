package com.sandwich.features.createOrder

import com.sandwich.features.CatalogItem
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  🔴 READ фаза: зібрати все потрібне для чистої функції
// ══════════════════════════════════════════════════════════════

fun GatherCreateOrderInput(
    readMenu: suspend () -> Map<String, CatalogItem>,
    readExtras: suspend () -> Map<String, CatalogItem>,
    generateId: () -> String,
    now: () -> Instant
): suspend (CreateOrderRequest) -> CreateOrderInput = { request ->
    CreateOrderInput(
        orderId = generateId(),
        customerName = request.customerName,
        items = request.items,
        menu = readMenu(),
        extras = readExtras(),
        now = now()
    )
}
