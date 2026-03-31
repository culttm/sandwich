package com.sandwich.features.orders.dispatchOrder

import com.sandwich.features.orders.Order
import com.sandwich.features.orders.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DispatchOrderLogicTest {

    private fun preparingOrder() = Order(
        id = "order-1",
        customerName = "Taras",
        items = emptyList(),
        subtotal = 120,
        discount = 0,
        deliveryFee = 50,
        total = 170,
        status = OrderStatus.PREPARING,
        createdAt = "2026-03-26T12:00:00Z"
    )

    private fun input(order: Order?) = DispatchOrderInput(order = order)

    @Test
    fun `PREPARING transitions to Dispatched`() {
        val result = dispatchOrder(input(preparingOrder()))

        assertIs<DispatchOrderDecision.Dispatched>(result)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, result.order.status)
    }

    @Test
    fun `null returns NotFound`() {
        val result = dispatchOrder(input(null))

        assertIs<DispatchOrderDecision.NotFound>(result)
    }

    @Test
    fun `DRAFT returns WrongStatus`() {
        val order = preparingOrder().copy(status = OrderStatus.DRAFT)

        val result = dispatchOrder(input(order))

        assertIs<DispatchOrderDecision.WrongStatus>(result)
        assertEquals(OrderStatus.DRAFT, result.current)
    }

    @Test
    fun `DELIVERED returns WrongStatus`() {
        val order = preparingOrder().copy(status = OrderStatus.DELIVERED)

        val result = dispatchOrder(input(order))

        assertIs<DispatchOrderDecision.WrongStatus>(result)
    }
}
