package com.sandwich.features.createOrder

import com.sandwich.features.CatalogItem
import com.sandwich.features.Order
import com.sandwich.features.OrderLine
import com.sandwich.features.OrderStatus
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
    val menu: Map<String, CatalogItem>,
    val extras: Map<String, CatalogItem>,
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
    val unknownSandwiches = input.items.map { it.sandwichId }.filter { it !in input.menu }
    val unknownExtras = input.items.flatMap { it.extras }.distinct().filter { it !in input.extras }
    val tooManyExtras = input.items.find { it.extras.size > MAX_EXTRAS_PER_SANDWICH }

    return when {
        input.customerName.isBlank() -> CreateOrderDecision.BlankName()
        input.items.isEmpty() -> CreateOrderDecision.EmptyOrder()
        input.items.size > MAX_ITEMS_PER_ORDER -> CreateOrderDecision.TooManyItems(MAX_ITEMS_PER_ORDER)
        unknownSandwiches.isNotEmpty() -> CreateOrderDecision.UnknownSandwich(unknownSandwiches)
        unknownExtras.isNotEmpty() -> CreateOrderDecision.UnknownExtras(unknownExtras)
        tooManyExtras != null -> CreateOrderDecision.TooManyExtras(tooManyExtras.sandwichId, MAX_EXTRAS_PER_SANDWICH)
        else -> buildOrder(input)
    }
}

private fun buildOrder(input: CreateOrderInput): CreateOrderDecision.Created {
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
