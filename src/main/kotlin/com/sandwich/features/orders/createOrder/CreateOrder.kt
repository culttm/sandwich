package com.sandwich.features.orders.createOrder

import com.sandwich.common.domain.*
import com.sandwich.common.infra.Db
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ══════════════════════════════════════════════════════════════
//  Крок 1: Створити чернетку замовлення → DRAFT
// ══════════════════════════════════════════════════════════════

// ── Request DTO ──

@Serializable
data class CreateOrderRequest(
    val customerName: String,
    val items: List<OrderItemRequest>
)

@Serializable
data class OrderItemRequest(
    val sandwichId: String,
    val extras: List<String> = emptyList()
)

// ── Decision ──

sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String = "Замовлення не може бути порожнім") : CreateOrderDecision
    data class BlankName(val message: String = "Вкажіть ім'я") : CreateOrderDecision
    data class TooManyItems(val max: Int) : CreateOrderDecision
    data class UnknownSandwich(val ids: List<String>) : CreateOrderDecision
    data class UnknownExtras(val ids: List<String>) : CreateOrderDecision
    data class TooManyExtras(val sandwichId: String, val max: Int) : CreateOrderDecision
}

// ── Pure logic (NOT suspend) ──

fun buildOrder(
    orderId: String,
    customerName: String,
    items: List<OrderItemRequest>,
    menu: Map<String, MenuItem>,
    extras: Map<String, ExtraItem>,
    now: Instant
): CreateOrderDecision {
    if (customerName.isBlank())
        return CreateOrderDecision.BlankName()

    if (items.isEmpty())
        return CreateOrderDecision.EmptyOrder()

    if (items.size > MAX_ITEMS_PER_ORDER)
        return CreateOrderDecision.TooManyItems(MAX_ITEMS_PER_ORDER)

    val unknownSandwiches = items.map { it.sandwichId }.filter { it !in menu }
    if (unknownSandwiches.isNotEmpty())
        return CreateOrderDecision.UnknownSandwich(unknownSandwiches)

    val unknownExtras = items.flatMap { it.extras }.distinct().filter { it !in extras }
    if (unknownExtras.isNotEmpty())
        return CreateOrderDecision.UnknownExtras(unknownExtras)

    val tooManyExtras = items.find { it.extras.size > MAX_EXTRAS_PER_SANDWICH }
    if (tooManyExtras != null)
        return CreateOrderDecision.TooManyExtras(tooManyExtras.sandwichId, MAX_EXTRAS_PER_SANDWICH)

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

    return CreateOrderDecision.Created(
        Order(
            id = orderId,
            customerName = customerName.trim(),
            items = lines,
            subtotal = subtotal,
            discount = discount,
            total = total,
            status = OrderStatus.DRAFT,
            createdAt = now.toString()
        )
    )
}

// ── Response DTOs ──

@Serializable
data class CreateOrderResponse(val orderId: String, val total: Int)

@Serializable
data class CreateOrderError(val error: String)

// ── Handler (Recawr Sandwich) ──

fun CreateOrder(db: Db): suspend (CreateOrderRequest) -> Any = handler@{ request ->

    // 🔴 READ
    val orderId = UUID.randomUUID().toString()
    val now = Instant.now()
    val menu = db.sandwiches.toMap()
    val extras = db.extras.toMap()

    // 🟢 CALCULATE
    val decision = buildOrder(orderId, request.customerName, request.items, menu, extras, now)

    // 🔴 WRITE
    when (decision) {
        is CreateOrderDecision.Created -> {
            db.orders[decision.order.id] = decision.order
            CreateOrderResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is CreateOrderDecision.BlankName -> CreateOrderError(decision.message)
        is CreateOrderDecision.EmptyOrder -> CreateOrderError(decision.message)
        is CreateOrderDecision.TooManyItems -> CreateOrderError("Максимум ${decision.max} сендвічів")
        is CreateOrderDecision.UnknownSandwich -> CreateOrderError("Невідомі сендвічі: ${decision.ids}")
        is CreateOrderDecision.UnknownExtras -> CreateOrderError("Невідомі додатки: ${decision.ids}")
        is CreateOrderDecision.TooManyExtras -> CreateOrderError("Макс ${decision.max} додатків для ${decision.sandwichId}")
    }
}
