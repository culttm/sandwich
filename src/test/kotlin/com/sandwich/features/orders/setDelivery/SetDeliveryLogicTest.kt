package com.sandwich.features.orders.setDelivery

import com.sandwich.common.domain.DELIVERY_BASE_FEE
import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SetDeliveryLogicTest {

    private fun draftOrder(subtotal: Int = 230, discount: Int = 0) = Order(
        id = "order-1",
        customerName = "Taras",
        items = emptyList(),
        subtotal = subtotal,
        discount = discount,
        total = subtotal - discount,
        status = OrderStatus.DRAFT,
        createdAt = "2026-03-26T12:00:00Z"
    )

    // -- Happy path --

    @Test
    fun `DRAFT order gets delivery and becomes AWAITING_PAYMENT`() {
        val order = draftOrder()

        val result = decideDelivery(order, "Khreshchatyk 1", "+380991234567", null)

        assertIs<SetDeliveryDecision.DeliverySet>(result)
        assertEquals(OrderStatus.AWAITING_PAYMENT, result.order.status)
        assertNotNull(result.order.delivery)
        assertEquals("Khreshchatyk 1", result.order.delivery!!.address)
        assertEquals("+380991234567", result.order.delivery!!.phone)
        assertEquals(DELIVERY_BASE_FEE, result.order.deliveryFee)
        assertEquals(230 + DELIVERY_BASE_FEE, result.order.total)
    }

    @Test
    fun `order over 500 gets free delivery`() {
        val order = draftOrder(subtotal = 600)

        val result = decideDelivery(order, "Khreshchatyk 1", "+380991234567", null)

        assertIs<SetDeliveryDecision.DeliverySet>(result)
        assertEquals(0, result.order.deliveryFee)
        assertEquals(600, result.order.total)
    }

    @Test
    fun `preferred delivery time is saved`() {
        val order = draftOrder()

        val result = decideDelivery(order, "Khreshchatyk 1", "+380991234567", "14:00")

        assertIs<SetDeliveryDecision.DeliverySet>(result)
        assertEquals("14:00", result.order.delivery!!.deliveryTime)
    }

    // -- Validation errors --

    @Test
    fun `null order returns NotFound`() {
        val result = decideDelivery(null, "Khreshchatyk 1", "+380991234567", null)

        assertIs<SetDeliveryDecision.NotFound>(result)
    }

    @Test
    fun `non-DRAFT order returns WrongStatus`() {
        val order = draftOrder().copy(status = OrderStatus.AWAITING_PAYMENT)

        val result = decideDelivery(order, "Khreshchatyk 1", "+380991234567", null)

        assertIs<SetDeliveryDecision.WrongStatus>(result)
        assertEquals(OrderStatus.AWAITING_PAYMENT, result.current)
    }

    @Test
    fun `blank address returns BlankAddress`() {
        val order = draftOrder()

        val result = decideDelivery(order, "  ", "+380991234567", null)

        assertIs<SetDeliveryDecision.BlankAddress>(result)
    }

    @Test
    fun `blank phone returns BlankPhone`() {
        val order = draftOrder()

        val result = decideDelivery(order, "Khreshchatyk 1", "", null)

        assertIs<SetDeliveryDecision.BlankPhone>(result)
    }
}
