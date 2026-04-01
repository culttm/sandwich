package com.sandwich.features.payOrder

import com.sandwich.features.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PayOrderLogicTest {

    private val now = Instant.parse("2026-03-26T12:00:00Z")
    private val txId = "tx-123"
    private val fullStock = mapOf("classic-club" to 50, "blt" to 35)

    private fun awaitingOrder(vararg sandwichIds: String) = Order(
        id = "order-1",
        customerName = "Taras",
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
        delivery = DeliveryInfo("Khreshchatyk 1", "+380991234567", null, 50),
        createdAt = "2026-03-26T11:50:00Z"
    )

    private fun input(
        order: Order = awaitingOrder("classic-club"),
        stock: Map<String, Int> = fullStock,
        method: PaymentMethod = PaymentMethod.CARD
    ) = PayOrderInput(order = order, stock = stock, method = method, now = now, transactionId = txId)

    // -- Happy path --

    @Test
    fun `card payment transitions to PREPARING and reserves stock`() {
        val order = awaitingOrder("classic-club", "blt")

        val result = payOrder(input(order = order))

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(OrderStatus.PREPARING, result.order.status)
        assertNotNull(result.order.payment)
        assertEquals(PaymentMethod.CARD, result.order.payment!!.method)
        assertEquals(txId, result.order.payment!!.transactionId)
        assertEquals(mapOf("classic-club" to 1, "blt" to 1), result.stockReductions)
    }

    @Test
    fun `cash on delivery also works`() {
        val result = payOrder(input(method = PaymentMethod.CASH_ON_DELIVERY))

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(PaymentMethod.CASH_ON_DELIVERY, result.order.payment!!.method)
    }

    @Test
    fun `two identical sandwiches reduce stock by 2`() {
        val order = awaitingOrder("classic-club", "classic-club")

        val result = payOrder(input(order = order))

        assertIs<PayOrderDecision.Paid>(result)
        assertEquals(mapOf("classic-club" to 2), result.stockReductions)
    }

    // -- Error cases --

    @Test
    fun `non-AWAITING_PAYMENT returns WrongStatus`() {
        val order = awaitingOrder("classic-club").copy(status = OrderStatus.DRAFT)

        val result = payOrder(input(order = order))

        assertIs<PayOrderDecision.WrongStatus>(result)
        assertEquals(OrderStatus.DRAFT, result.current)
    }

    @Test
    fun `zero stock returns OutOfStock`() {
        val result = payOrder(input(stock = mapOf("classic-club" to 0)))

        assertIs<PayOrderDecision.OutOfStock>(result)
        assertEquals(listOf("classic-club"), result.unavailable)
    }

    @Test
    fun `insufficient stock for quantity returns OutOfStock`() {
        val order = awaitingOrder("classic-club", "classic-club", "classic-club")

        val result = payOrder(input(order = order, stock = mapOf("classic-club" to 2)))

        assertIs<PayOrderDecision.OutOfStock>(result)
    }
}
