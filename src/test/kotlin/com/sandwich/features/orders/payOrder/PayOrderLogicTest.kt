package com.sandwich.features.orders.payOrder

import com.sandwich.common.domain.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PayOrderLogicTest {

    private val now = Instant.parse("2026-03-26T12:00:00Z")
    private val txId = "tx-123"

    private fun awaitingOrder(vararg sandwichIds: String) = Order(
        id = "order-1",
        customerName = "Тарас",
        items = sandwichIds.map { id ->
            OrderLine(
                sandwichId = id,
                sandwichName = id,
                sandwichPrice = 120,
                extras = emptyList(),
                lineTotal = 120
            )
        },
        subtotal = sandwichIds.size * 120,
        discount = 0,
        deliveryFee = 50,
        total = sandwichIds.size * 120 + 50,
        status = OrderStatus.AWAITING_PAYMENT,
        delivery = DeliveryInfo("вул. Хрещатик 1", "+380991234567", null, 50),
        createdAt = "2026-03-26T11:50:00Z"
    )

    private val fullStock = mapOf("classic-club" to 50, "blt" to 35)

    // ── Happy path ──

    @Test
    fun `оплата карткою — PREPARING + stock reserved`() {
        val order = awaitingOrder("classic-club", "blt")

        val result = decidePayment(order, fullStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(OrderStatus.PREPARING, result.order.status)
        assertNotNull(result.order.payment)
        assertEquals(PaymentMethod.CARD, result.order.payment!!.method)
        assertEquals(txId, result.order.payment!!.transactionId)
        assertEquals(mapOf("classic-club" to 1, "blt" to 1), result.stockReductions)
    }

    @Test
    fun `оплата при доставці — теж працює`() {
        val order = awaitingOrder("classic-club")

        val result = decidePayment(order, fullStock, PaymentMethod.CASH_ON_DELIVERY, now, txId)

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(PaymentMethod.CASH_ON_DELIVERY, result.order.payment!!.method)
    }

    @Test
    fun `два однакові сендвічі — stock reduction = 2`() {
        val order = awaitingOrder("classic-club", "classic-club")

        val result = decidePayment(order, fullStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(mapOf("classic-club" to 2), result.stockReductions)
    }

    // ── Error cases ──

    @Test
    fun `null order — NotFound`() {
        val result = decidePayment(null, fullStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.NotFound>(result)
    }

    @Test
    fun `не AWAITING_PAYMENT — WrongStatus`() {
        val order = awaitingOrder("classic-club").copy(status = OrderStatus.DRAFT)

        val result = decidePayment(order, fullStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.WrongStatus>(result)
        assertEquals(OrderStatus.DRAFT, result.current)
    }

    @Test
    fun `немає в наявності — OutOfStock`() {
        val order = awaitingOrder("classic-club")
        val emptyStock = mapOf("classic-club" to 0)

        val result = decidePayment(order, emptyStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.OutOfStock>(result)
        assertEquals(listOf("classic-club"), result.unavailable)
    }

    @Test
    fun `недостатньо stock для кількості — OutOfStock`() {
        val order = awaitingOrder("classic-club", "classic-club", "classic-club")
        val lowStock = mapOf("classic-club" to 2)

        val result = decidePayment(order, lowStock, PaymentMethod.CARD, now, txId)

        assertIs<PayOrderDecision.OutOfStock>(result)
    }
}
