package com.sandwich.features.orders.createOrder

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.MenuItem
import com.sandwich.common.domain.OrderStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Тести чистої функції buildOrder.
 * Жодних моків, жодного runTest, жодних корутин.
 */
class CreateOrderLogicTest {

    private val menu = mapOf(
        "classic-club" to MenuItem("classic-club", "Classic Club", 120),
        "blt" to MenuItem("blt", "BLT", 110),
        "veggie-delight" to MenuItem("veggie-delight", "Veggie Delight", 99),
    )

    private val extras = mapOf(
        "extra-cheese" to ExtraItem("extra-cheese", "Сир", 25),
        "bacon" to ExtraItem("bacon", "Бекон", 35),
        "jalapenos" to ExtraItem("jalapenos", "Халапеньо", 15),
    )

    private val now = Instant.parse("2026-03-26T12:00:00Z")
    private val orderId = "test-order-1"

    // ── Happy path ──

    @Test
    fun `один сендвіч — створює DRAFT`() {
        val items = listOf(OrderItemRequest("classic-club"))

        val result = buildOrder(orderId, "Тарас", items, menu, extras, now)

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(OrderStatus.DRAFT, result.order.status)
        assertEquals(120, result.order.total)
        assertEquals(0, result.order.discount)
        assertEquals(0, result.order.deliveryFee)
        assertNull(result.order.delivery)
        assertNull(result.order.payment)
        assertEquals("Тарас", result.order.customerName)
    }

    @Test
    fun `сендвіч з extras — ціна складається`() {
        val items = listOf(
            OrderItemRequest("blt", listOf("extra-cheese", "bacon"))
        )

        val result = buildOrder(orderId, "Оля", items, menu, extras, now)

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(170, result.order.total) // BLT(110) + cheese(25) + bacon(35)
        assertEquals(1, result.order.items.size)
        assertEquals(2, result.order.items[0].extras.size)
    }

    @Test
    fun `3+ сендвічі — знижка 10 відсотків`() {
        val items = listOf(
            OrderItemRequest("classic-club"),   // 120
            OrderItemRequest("blt"),             // 110
            OrderItemRequest("veggie-delight"),  //  99
        )

        val result = buildOrder(orderId, "Марія", items, menu, extras, now)

        assertIs<CreateOrderDecision.Created>(result)
        val subtotal = 120 + 110 + 99  // 329
        val discount = subtotal * 10 / 100  // 32
        assertEquals(subtotal, result.order.subtotal)
        assertEquals(discount, result.order.discount)
        assertEquals(subtotal - discount, result.order.total)
    }

    @Test
    fun `2 сендвічі — без знижки`() {
        val items = listOf(
            OrderItemRequest("classic-club"),
            OrderItemRequest("blt"),
        )

        val result = buildOrder(orderId, "Ігор", items, menu, extras, now)

        assertIs<CreateOrderDecision.Created>(result)
        assertEquals(0, result.order.discount)
        assertEquals(230, result.order.total)
    }

    // ── Validation errors ──

    @Test
    fun `порожнє ім'я — BlankName`() {
        val items = listOf(OrderItemRequest("classic-club"))

        val result = buildOrder(orderId, "  ", items, menu, extras, now)

        assertIs<CreateOrderDecision.BlankName>(result)
    }

    @Test
    fun `порожнє замовлення — EmptyOrder`() {
        val result = buildOrder(orderId, "Тарас", emptyList(), menu, extras, now)

        assertIs<CreateOrderDecision.EmptyOrder>(result)
    }

    @Test
    fun `більше 10 позицій — TooManyItems`() {
        val items = (1..11).map { OrderItemRequest("classic-club") }

        val result = buildOrder(orderId, "Тарас", items, menu, extras, now)

        assertIs<CreateOrderDecision.TooManyItems>(result)
        assertEquals(10, result.max)
    }

    @Test
    fun `невідомий сендвіч — UnknownSandwich`() {
        val items = listOf(OrderItemRequest("mega-burger"))

        val result = buildOrder(orderId, "Тарас", items, menu, extras, now)

        assertIs<CreateOrderDecision.UnknownSandwich>(result)
        assertEquals(listOf("mega-burger"), result.ids)
    }

    @Test
    fun `невідомий extra — UnknownExtras`() {
        val items = listOf(OrderItemRequest("blt", listOf("truffle-oil")))

        val result = buildOrder(orderId, "Тарас", items, menu, extras, now)

        assertIs<CreateOrderDecision.UnknownExtras>(result)
        assertEquals(listOf("truffle-oil"), result.ids)
    }

    @Test
    fun `більше 5 extras — TooManyExtras`() {
        val tooMany = listOf("extra-cheese", "bacon", "jalapenos", "extra-cheese", "bacon", "jalapenos")
        val items = listOf(OrderItemRequest("blt", tooMany))

        val result = buildOrder(orderId, "Тарас", items, menu, extras, now)

        assertIs<CreateOrderDecision.TooManyExtras>(result)
        assertEquals("blt", result.sandwichId)
        assertEquals(5, result.max)
    }
}
