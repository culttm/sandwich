package com.sandwich.features.setDelivery

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus
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

    private fun input(
        order: Order = draftOrder(),
        address: String = "Khreshchatyk 1",
        phone: String = "+380991234567",
        deliveryTime: String? = null
    ) = SetDeliveryInput(order = order, address = address, phone = phone, deliveryTime = deliveryTime)

    // -- Happy path --

    @Test
    fun `DRAFT order gets delivery and becomes AWAITING_PAYMENT`() {
        val result = setDelivery(input())

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
        val result = setDelivery(input(order = draftOrder(subtotal = 600)))

        assertIs<SetDeliveryDecision.DeliverySet>(result)
        assertEquals(0, result.order.deliveryFee)
        assertEquals(600, result.order.total)
    }

    @Test
    fun `preferred delivery time is saved`() {
        val result = setDelivery(input(deliveryTime = "14:00"))

        assertIs<SetDeliveryDecision.DeliverySet>(result)
        assertEquals("14:00", result.order.delivery!!.deliveryTime)
    }

    // -- Validation errors --

    @Test
    fun `non-DRAFT order returns WrongStatus`() {
        val order = draftOrder().copy(status = OrderStatus.AWAITING_PAYMENT)

        val result = setDelivery(input(order = order))

        assertIs<SetDeliveryDecision.WrongStatus>(result)
        assertEquals(OrderStatus.AWAITING_PAYMENT, result.current)
    }

    @Test
    fun `blank address returns BlankAddress`() {
        val result = setDelivery(input(address = "  "))

        assertIs<SetDeliveryDecision.BlankAddress>(result)
    }

    @Test
    fun `blank phone returns BlankPhone`() {
        val result = setDelivery(input(phone = ""))

        assertIs<SetDeliveryDecision.BlankPhone>(result)
    }
}
