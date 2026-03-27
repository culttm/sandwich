package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.*
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
        customerName = "Тарас",
        items = emptyList(),
        subtotal = 120,
        discount = 0,
        total = 120,
        status = OrderStatus.DRAFT,
        createdAt = createdAt
    )

    private fun preparingOrder(createdAt: String = "2026-03-26T11:50:00Z") = Order(
        id = "order-1",
        customerName = "Тарас",
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
        delivery = DeliveryInfo("вул. Хрещатик 1", "+380991234567", null, 50),
        createdAt = createdAt
    )

    // ── Happy path: DRAFT → cancel без refund ──

    @Test
    fun `DRAFT в межах вікна — Cancelled, без refund, без release stock`() {
        val result = decideCancellation(draftOrder(), now)

        assertIs<CancelDecision.Cancelled>(result)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
        assertFalse(result.refund)
        assertTrue(result.releaseStock.isEmpty())
    }

    // ── Happy path: AWAITING_PAYMENT → cancel без refund ──

    @Test
    fun `AWAITING_PAYMENT — Cancelled, без refund`() {
        val order = draftOrder().copy(status = OrderStatus.AWAITING_PAYMENT)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.Cancelled>(result)
        assertFalse(result.refund)
        assertTrue(result.releaseStock.isEmpty())
    }

    // ── Happy path: PREPARING → cancel з refund + release stock ──

    @Test
    fun `PREPARING — Cancelled з refund та release stock`() {
        val result = decideCancellation(preparingOrder(), now)

        assertIs<CancelDecision.Cancelled>(result)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
        assertTrue(result.refund)
        assertEquals(mapOf("classic-club" to 1, "blt" to 1), result.releaseStock)
    }

    // ── Error cases ──

    @Test
    fun `null order — NotFound`() {
        val result = decideCancellation(null, now)

        assertIs<CancelDecision.NotFound>(result)
    }

    @Test
    fun `вже скасоване — AlreadyCancelled`() {
        val order = draftOrder().copy(status = OrderStatus.CANCELLED)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.AlreadyCancelled>(result)
    }

    @Test
    fun `OUT_FOR_DELIVERY — TooLate`() {
        val order = draftOrder().copy(status = OrderStatus.OUT_FOR_DELIVERY)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.TooLate>(result)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, result.status)
    }

    @Test
    fun `DELIVERED — TooLate`() {
        val order = draftOrder().copy(status = OrderStatus.DELIVERED)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.TooLate>(result)
    }

    @Test
    fun `DRAFT але більше 15 хвилин — WindowExpired`() {
        val order = draftOrder(createdAt = "2026-03-26T11:30:00Z") // 30 хв тому

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.WindowExpired>(result)
        assertEquals(15L, result.maxMinutes)
    }

    @Test
    fun `рівно на межі 15 хв — ще можна скасувати`() {
        val order = draftOrder(createdAt = "2026-03-26T11:45:00Z") // рівно 15 хв

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.Cancelled>(result)
    }
}
