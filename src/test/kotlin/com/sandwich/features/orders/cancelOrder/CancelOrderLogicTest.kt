package com.sandwich.features.orders.cancelOrder

import com.sandwich.common.domain.Order
import com.sandwich.common.domain.OrderStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Тести чистої функції decideCancellation.
 * Жодних моків, жодного runTest.
 */
class CancelOrderLogicTest {

    private val now = Instant.parse("2026-03-26T12:00:00Z")

    private fun pendingOrder(createdAt: String = "2026-03-26T11:50:00Z") = Order(
        id = "order-1",
        customerName = "Тарас",
        items = emptyList(),
        subtotal = 120,
        discount = 0,
        total = 120,
        status = OrderStatus.PENDING,
        createdAt = createdAt
    )

    // ── Happy path ──

    @Test
    fun `PENDING замовлення в межах вікна — Cancelled`() {
        val order = pendingOrder(createdAt = "2026-03-26T11:50:00Z") // 10 хв тому

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.Cancelled>(result)
        assertEquals(OrderStatus.CANCELLED, result.order.status)
        assertEquals("order-1", result.order.id)
    }

    // ── Error cases ──

    @Test
    fun `null order — NotFound`() {
        val result = decideCancellation(null, now)

        assertIs<CancelDecision.NotFound>(result)
    }

    @Test
    fun `вже скасоване — AlreadyCancelled`() {
        val order = pendingOrder().copy(status = OrderStatus.CANCELLED)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.AlreadyCancelled>(result)
    }

    @Test
    fun `PREPARING — TooLate`() {
        val order = pendingOrder().copy(status = OrderStatus.PREPARING)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.TooLate>(result)
        assertEquals(OrderStatus.PREPARING, result.status)
    }

    @Test
    fun `READY — TooLate`() {
        val order = pendingOrder().copy(status = OrderStatus.READY)

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.TooLate>(result)
    }

    @Test
    fun `PENDING але більше 15 хвилин — WindowExpired`() {
        val order = pendingOrder(createdAt = "2026-03-26T11:30:00Z") // 30 хв тому

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.WindowExpired>(result)
        assertEquals(15L, result.maxMinutes)
    }

    @Test
    fun `рівно на межі 15 хв — ще можна скасувати`() {
        val order = pendingOrder(createdAt = "2026-03-26T11:45:00Z") // рівно 15 хв

        val result = decideCancellation(order, now)

        assertIs<CancelDecision.Cancelled>(result)
    }
}
