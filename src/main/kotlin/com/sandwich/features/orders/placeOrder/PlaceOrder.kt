package com.sandwich.features.orders.placeOrder

import com.sandwich.common.domain.*
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Level 3: Impureim Sandwich (Recawr)
// ══════════════════════════════════════════════════════════════

// ── Request DTO ──

@Serializable
data class PlaceOrderRequest(
    val customerName: String,
    val items: List<OrderItemRequest>
)

@Serializable
data class OrderItemRequest(
    val sandwichId: String,
    val extras: List<String> = emptyList()
)

// ── Decision ──

sealed interface PlaceOrderDecision {
    data class Accepted(val order: Order) : PlaceOrderDecision
    data class EmptyOrder(val message: String = "Замовлення не може бути порожнім") : PlaceOrderDecision
    data class BlankName(val message: String = "Вкажіть ім'я") : PlaceOrderDecision
    data class TooManyItems(val max: Int) : PlaceOrderDecision
    data class UnknownSandwich(val ids: List<String>) : PlaceOrderDecision
    data class UnknownExtras(val ids: List<String>) : PlaceOrderDecision
    data class TooManyExtras(val sandwichId: String, val max: Int) : PlaceOrderDecision
}

// ── Pure logic (NOT suspend) ──

fun buildOrder(
    orderId: String,
    customerName: String,
    items: List<OrderItemRequest>,
    menu: Map<String, MenuItem>,
    extras: Map<String, ExtraItem>,
    now: Instant
): PlaceOrderDecision {
    if (customerName.isBlank())
        return PlaceOrderDecision.BlankName()

    if (items.isEmpty())
        return PlaceOrderDecision.EmptyOrder()

    if (items.size > MAX_ITEMS_PER_ORDER)
        return PlaceOrderDecision.TooManyItems(MAX_ITEMS_PER_ORDER)

    val unknownSandwiches = items.map { it.sandwichId }.filter { it !in menu }
    if (unknownSandwiches.isNotEmpty())
        return PlaceOrderDecision.UnknownSandwich(unknownSandwiches)

    val unknownExtras = items.flatMap { it.extras }.distinct().filter { it !in extras }
    if (unknownExtras.isNotEmpty())
        return PlaceOrderDecision.UnknownExtras(unknownExtras)

    val tooManyExtras = items.find { it.extras.size > MAX_EXTRAS_PER_SANDWICH }
    if (tooManyExtras != null)
        return PlaceOrderDecision.TooManyExtras(tooManyExtras.sandwichId, MAX_EXTRAS_PER_SANDWICH)

    val lines = items.map { item ->
        val sandwich = menu.getValue(item.sandwichId)
        val itemExtras = item.extras.map { extras.getValue(it) }
        val lineTotal = calculateLineTotal(sandwich.price, itemExtras.map { it.price })
        OrderLine(
            sandwichId = sandwich.id,
            sandwichName = sandwich.name,
            sandwichPrice = sandwich.price,
            extras = itemExtras,
            lineTotal = lineTotal
        )
    }

    val subtotal = lines.sumOf { it.lineTotal }
    val discount = calculateDiscount(lines.size, subtotal)
    val total = subtotal - discount

    return PlaceOrderDecision.Accepted(
        Order(
            id = orderId,
            customerName = customerName.trim(),
            items = lines,
            subtotal = subtotal,
            discount = discount,
            total = total,
            status = OrderStatus.PENDING,
            createdAt = now.toString()
        )
    )
}

// ── Response DTOs ──

@Serializable
data class PlaceOrderResponse(val orderId: String, val total: Int)

@Serializable
data class ErrorResponse(val error: String)

// ── Handler (Recawr Sandwich) — приймає тільки Db ──

fun PlaceOrder(db: Db): suspend (PlaceOrderRequest) -> Any = handler@{ request ->

    // 🔴 READ
    val orderId = UUID.randomUUID().toString()
    val now = Instant.now()
    val menu = db.sandwiches.toMap()
    val extras = db.extras.toMap()

    // 🟢 CALCULATE
    val decision = buildOrder(orderId, request.customerName, request.items, menu, extras, now)

    // 🔴 WRITE
    when (decision) {
        is PlaceOrderDecision.Accepted -> {
            db.orders[decision.order.id] = decision.order
            PlaceOrderResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is PlaceOrderDecision.BlankName -> ErrorResponse(decision.message)
        is PlaceOrderDecision.EmptyOrder -> ErrorResponse(decision.message)
        is PlaceOrderDecision.TooManyItems -> ErrorResponse("Максимум ${decision.max} сендвічів")
        is PlaceOrderDecision.UnknownSandwich -> ErrorResponse("Невідомі сендвічі: ${decision.ids}")
        is PlaceOrderDecision.UnknownExtras -> ErrorResponse("Невідомі додатки: ${decision.ids}")
        is PlaceOrderDecision.TooManyExtras -> ErrorResponse("Макс ${decision.max} додатків для ${decision.sandwichId}")
    }
}
