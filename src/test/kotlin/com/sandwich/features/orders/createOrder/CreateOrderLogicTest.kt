package com.sandwich.features.orders.createOrder

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.MenuItem
import com.sandwich.common.domain.OrderStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CreateOrderLogicTest {

    private val menu = mapOf(
        "classic-club" to MenuItem("classic-club", "Classic Club", 120),
        "blt" to MenuItem("blt", "BLT", 110),
        "veggie-delight" to MenuItem("veggie-delight", "Veggie Delight", 99),
    )

    private val extras = mapOf(
        "extra-cheese" to ExtraItem("extra-cheese", "Cheese", 25),
        "bacon" to ExtraItem("bacon", "Bacon", 35),
        "jalapenos" to ExtraItem("jalapenos", "Jalapenos", 15),
    )

    private val now = Instant.parse("2026-03-26T12:00:00Z")
    private val orderId = "test-order-1"

    private fun input(
        customerName: String = "Taras",
        items: List<OrderItemRequest> = listOf(OrderItemRequest("classic-club"))
    ) = CreateOrderInput(
        orderId = orderId,
        customerName = customerName,
        items = items,
        menu = menu,
        extras = extras,
        now = now
    )

    // -- Happy path --

    @Test
    fun `single sandwich creates DRAFT`() {
        val result = buildOrder(input(items = listOf(OrderItemRequest("classic-club"))))

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(OrderStatus.DRAFT, result.order.status)
        assertEquals(120, result.order.total)
        assertEquals(0, result.order.discount)
        assertEquals(0, result.order.deliveryFee)
        assertNull(result.order.delivery)
        assertNull(result.order.payment)
        assertEquals("Taras", result.order.customerName)
    }

    @Test
    fun `sandwich with extras sums prices`() {
        val result = buildOrder(input(
            customerName = "Olya",
            items = listOf(OrderItemRequest("blt", listOf("extra-cheese", "bacon")))
        ))

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(170, result.order.total) // BLT(110) + cheese(25) + bacon(35)
        assertEquals(1, result.order.items.size)
        assertEquals(2, result.order.items[0].extras.size)
    }

    @Test
    fun `3 or more sandwiches get 10 percent discount`() {
        val result = buildOrder(input(
            customerName = "Maria",
            items = listOf(
                OrderItemRequest("classic-club"),   // 120
                OrderItemRequest("blt"),             // 110
                OrderItemRequest("veggie-delight"),  //  99
            )
        ))

        assertIs<CreateOrderDecision.Created>(result)
        val subtotal = 120 + 110 + 99  // 329
        val discount = subtotal * 10 / 100  // 32
        assertEquals(subtotal, result.order.subtotal)
        assertEquals(discount, result.order.discount)
        assertEquals(subtotal - discount, result.order.total)
    }

    @Test
    fun `2 sandwiches no discount`() {
        val result = buildOrder(input(
            customerName = "Igor",
            items = listOf(
                OrderItemRequest("classic-club"),
                OrderItemRequest("blt"),
            )
        ))

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(0, result.order.discount)
        assertEquals(230, result.order.total)
    }

    // -- Validation errors --

    @Test
    fun `blank name returns BlankName`() {
        val result = buildOrder(input(customerName = "  "))

        assertIs<CreateOrderDecision.BlankName>(result)
    }

    @Test
    fun `empty items returns EmptyOrder`() {
        val result = buildOrder(input(items = emptyList()))

        assertIs<CreateOrderDecision.EmptyOrder>(result)
    }

    @Test
    fun `more than 10 items returns TooManyItems`() {
        val result = buildOrder(input(items = (1..11).map { OrderItemRequest("classic-club") }))

        assertIs<CreateOrderDecision.TooManyItems>(result)
        assertEquals(10, result.max)
    }

    @Test
    fun `unknown sandwich returns UnknownSandwich`() {
        val result = buildOrder(input(items = listOf(OrderItemRequest("mega-burger"))))

        assertIs<CreateOrderDecision.UnknownSandwich>(result)
        assertEquals(listOf("mega-burger"), result.ids)
    }

    @Test
    fun `unknown extra returns UnknownExtras`() {
        val result = buildOrder(input(items = listOf(OrderItemRequest("blt", listOf("truffle-oil")))))

        assertIs<CreateOrderDecision.UnknownExtras>(result)
        assertEquals(listOf("truffle-oil"), result.ids)
    }

    @Test
    fun `more than 5 extras returns TooManyExtras`() {
        val tooMany = listOf("extra-cheese", "bacon", "jalapenos", "extra-cheese", "bacon", "jalapenos")
        val result = buildOrder(input(items = listOf(OrderItemRequest("blt", tooMany))))

        assertIs<CreateOrderDecision.TooManyExtras>(result)
        assertEquals("blt", result.sandwichId)
        assertEquals(5, result.max)
    }
}
