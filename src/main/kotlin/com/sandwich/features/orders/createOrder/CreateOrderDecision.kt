package com.sandwich.features.orders.createOrder

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.MenuItem
import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderLine
import com.sandwich.features.orders.OrderStatus
import kotlinx.serialization.Serializable
import java.time.Instant

// ══════════════════════════════════════════════════════════════
//  Чистий домен: типи + pure-логіка (жодного IO, жодного suspend)
// ══════════════════════════════════════════════════════════════

// ── Shared value type (використовується і в HTTP, і в домені) ──

@Serializable
data class OrderItemRequest(
    val sandwichId: String,
    val extras: List<String> = emptyList()
)

// ── Вхід для чистої функції (зібрано на фазі READ) ──

data class CreateOrderInput(
    val orderId: String,
    val customerName: String,
    val items: List<OrderItemRequest>,
    val menu: Map<String, MenuItem>,
    val extras: Map<String, ExtraItem>,
    val now: Instant
)

// ── Рішення (результат чистої функції) ──

sealed interface CreateOrderDecision {
    data class Created(val order: Order) : CreateOrderDecision
    data class EmptyOrder(val message: String = "Замовлення не може бути порожнім") : CreateOrderDecision
    data class BlankName(val message: String = "Вкажіть ім'я") : CreateOrderDecision
    data class TooManyItems(val max: Int) : CreateOrderDecision
    data class UnknownSandwich(val ids: List<String>) : CreateOrderDecision
    data class UnknownExtras(val ids: List<String>) : CreateOrderDecision
    data class TooManyExtras(val sandwichId: String, val max: Int) : CreateOrderDecision
}

// ── Pure logic ──

fun createOrder(input: CreateOrderInput): CreateOrderDecision {
    if (input.customerName.isBlank())
        return CreateOrderDecision.BlankName()

    if (input.items.isEmpty())
        return CreateOrderDecision.EmptyOrder()

    if (input.items.size > MAX_ITEMS_PER_ORDER)
        return CreateOrderDecision.TooManyItems(MAX_ITEMS_PER_ORDER)

    val unknownSandwiches = input.items.map { it.sandwichId }.filter { it !in input.menu }
    if (unknownSandwiches.isNotEmpty())
        return CreateOrderDecision.UnknownSandwich(unknownSandwiches)

    val unknownExtras = input.items.flatMap { it.extras }.distinct().filter { it !in input.extras }
    if (unknownExtras.isNotEmpty())
        return CreateOrderDecision.UnknownExtras(unknownExtras)

    val tooManyExtras = input.items.find { it.extras.size > MAX_EXTRAS_PER_SANDWICH }
    if (tooManyExtras != null)
        return CreateOrderDecision.TooManyExtras(tooManyExtras.sandwichId, MAX_EXTRAS_PER_SANDWICH)

    val lines = input.items.map { item ->
        val sandwich = input.menu.getValue(item.sandwichId)
        val itemExtras = item.extras.map { input.extras.getValue(it) }
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
            id = input.orderId,
            customerName = input.customerName.trim(),
            items = lines,
            subtotal = subtotal,
            discount = discount,
            total = total,
            status = OrderStatus.DRAFT,
            createdAt = input.now.toString()
        )
    )
}
