package com.sandwich.features.cancelOrder

import com.sandwich.features.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CancelOrderLogicTest {

    private val now = Instant.parse("2026-03-26T12:00:00Z")

    private fun draftOrder(createdAt: String = "2026-03-26T11:50:00Z") = Order(
        id = "order-1",
        customerName = "Taras",
        items = emptyList(),
        subtotal = 120,
        discount = 0,
        total = 120,
        status = OrderStatus.DRAFT,
        createdAt = createdAt
    )

    private fun preparingOrder(createdAt: String = "2026-03-26T11:50:00Z") = Order(
        id = "order-1",
        customerName = "Taras",
        items = listOf(
            OrderLine("classic-club", "Classic Club", 120, emptyList(), 120),
            OrderLine("blt", "BLT", 110, emptyList(), 110),
        ),
        subtotal = 230,
        discount = 0,
        deliveryFee = 50,
        total = 280,
        status = OrderStatus.PREPARING,
        payment = PaymentInfo(PaymentMethod.CARD, "2026-03-26T11:55:00Z", "tx-1"),
        delivery = DeliveryInfo("Khreshchatyk 1", "+380991234567", null, 50),
        createdAt = createdAt
    )

    private fun input(order: Order?) = CancelOrderInput(order = order, now = now)

    // -- Happy path: DRAFT cancel without refund --

    @Test
    fun `DRAFT within window is Cancelled without refund or stock release`() {
        val result = cancelOrder(input(draftOrder()))

        assertIs<CancelOrderDecision.Cancelled>(result)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
        assertFalse(result.refund)
        assertTrue(result.releaseStock.isEmpty())
    }

    // -- Happy path: AWAITING_PAYMENT cancel without refund --

    @Test
    fun `AWAITING_PAYMENT is Cancelled without refund`() {
        val order = draftOrder().copy(status = OrderStatus.AWAITING_PAYMENT)

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.Cancelled>(result)
        assertFalse(result.refund)
        assertTrue(result.releaseStock.isEmpty())
    }

    // -- Happy path: PREPARING cancel with refund + stock release --

    @Test
    fun `PREPARING is Cancelled with refund and stock release`() {
        val result = cancelOrder(input(preparingOrder()))

        assertIs<CancelOrderDecision.Cancelled>(result)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
        assertTrue(result.refund)
        assertEquals(mapOf("classic-club" to 1, "blt" to 1), result.releaseStock)
    }

    // -- Error cases --

    @Test
    fun `null order returns NotFound`() {
        val result = cancelOrder(input(null))

        assertIs<CancelOrderDecision.NotFound>(result)
    }

    @Test
    fun `already cancelled returns AlreadyCancelled`() {
        val order = draftOrder().copy(status = OrderStatus.CANCELLED)

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.AlreadyCancelled>(result)
    }

    @Test
    fun `OUT_FOR_DELIVERY returns TooLate`() {
        val order = draftOrder().copy(status = OrderStatus.OUT_FOR_DELIVERY)

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.TooLate>(result)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, result.status)
    }

    @Test
    fun `DELIVERED returns TooLate`() {
        val order = draftOrder().copy(status = OrderStatus.DELIVERED)

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.TooLate>(result)
    }

    @Test
    fun `DRAFT older than 15 minutes returns WindowExpired`() {
        val order = draftOrder(createdAt = "2026-03-26T11:30:00Z") // 30 min ago

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.WindowExpired>(result)
        assertEquals(15L, result.maxMinutes)
    }

    @Test
    fun `exactly at 15 min boundary is still cancellable`() {
        val order = draftOrder(createdAt = "2026-03-26T11:45:00Z") // exactly 15 min

        val result = cancelOrder(input(order))

        assertIs<CancelOrderDecision.Cancelled>(result)
    }
}
