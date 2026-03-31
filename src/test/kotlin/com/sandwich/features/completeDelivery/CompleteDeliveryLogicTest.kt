package com.sandwich.features.completeDelivery

import com.sandwich.features.Order
import com.sandwich.features.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CompleteDeliveryLogicTest {

    private fun outForDeliveryOrder() = Order(
        id = "order-1",
        customerName = "Taras",
        items = emptyList(),
        subtotal = 120,
        discount = 0,
        deliveryFee = 50,
        total = 170,
        status = OrderStatus.OUT_FOR_DELIVERY,
        createdAt = "2026-03-26T12:00:00Z"
    )

    private fun input(order: Order?) = CompleteDeliveryInput(order = order)

    @Test
    fun `OUT_FOR_DELIVERY transitions to Delivered`() {
        val result = completeDelivery(input(outForDeliveryOrder()))

        assertIs<CompleteDeliveryDecision.Delivered>(result)
        assertEquals(OrderStatus.DELIVERED, result.order.status)
    }

    @Test
    fun `null returns NotFound`() {
        val result = completeDelivery(input(null))

        assertIs<CompleteDeliveryDecision.NotFound>(result)
    }

    @Test
    fun `PREPARING returns WrongStatus`() {
        val order = outForDeliveryOrder().copy(status = OrderStatus.PREPARING)

        val result = completeDelivery(input(order))

        assertIs<CompleteDeliveryDecision.WrongStatus>(result)
        assertEquals(OrderStatus.PREPARING, result.current)
    }

    @Test
    fun `CANCELLED returns WrongStatus`() {
        val order = outForDeliveryOrder().copy(status = OrderStatus.CANCELLED)

        val result = completeDelivery(input(order))

        assertIs<CompleteDeliveryDecision.WrongStatus>(result)
    }
}
