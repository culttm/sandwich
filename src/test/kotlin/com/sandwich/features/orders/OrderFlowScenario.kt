package com.sandwich.features.orders

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// ══════════════════════════════════════════════════════════════
//  Abstract order flow scenarios — WHAT we test, not HOW
// ══════════════════════════════════════════════════════════════

abstract class OrderFlowScenario {

    // ====================================================
    //  Happy path: full order lifecycle
    // ====================================================

    @Test
    fun `full flow - create, delivery, pay, dispatch, complete`() = runTest {
        val orderId = createOrder("Taras", listOf(
            OrderItemData("classic-club"),
            OrderItemData("blt", listOf("bacon"))
        ))
        expectOrderStatus(orderId, "DRAFT")

        setDelivery(orderId, "Khreshchatyk 1, Kyiv", "+380991234567")
        expectOrderStatus(orderId, "AWAITING_PAYMENT")
        expectOrderHasDelivery(orderId)

        payOrder(orderId, "CARD")
        expectOrderStatus(orderId, "PREPARING")
        expectOrderHasPayment(orderId)

        dispatchOrder(orderId)
        expectOrderStatus(orderId, "OUT_FOR_DELIVERY")

        completeDelivery(orderId)
        expectOrderStatus(orderId, "DELIVERED")
    }

    // ====================================================
    //  Cancellation at different stages
    // ====================================================

    @Test
    fun `cancel DRAFT - cancelled without refund`() = runTest {
        val orderId = createOrder("Olya", listOf(OrderItemData("veggie-delight")))

        val result = cancelOrder(orderId)

        assertEquals(false, result.refund)
        expectOrderStatus(orderId, "CANCELLED")
    }

    @Test
    fun `cancel AWAITING_PAYMENT - cancelled without refund`() = runTest {
        val orderId = createAndSetDelivery("classic-club")

        val result = cancelOrder(orderId)

        assertEquals(false, result.refund)
    }

    @Test
    fun `cancel PREPARING - cancelled with refund and stock release`() = runTest {
        val orderId = createDeliveryAndPay("classic-club")
        val stockBefore = getStock("classic-club")

        val result = cancelOrder(orderId)

        assertEquals(true, result.refund)
        assertEquals(stockBefore + 1, getStock("classic-club"))
    }

    @Test
    fun `cancel OUT_FOR_DELIVERY - too late`() = runTest {
        val orderId = createDeliveryAndPay("classic-club")
        dispatchOrder(orderId)

        expectCancelError(orderId)
    }

    // ====================================================
    //  Stock reservation
    // ====================================================

    @Test
    fun `pay decreases stock`() = runTest {
        val stockBefore = getStock("classic-club")

        createDeliveryAndPay("classic-club")

        assertEquals(stockBefore - 1, getStock("classic-club"))
    }

    @Test
    fun `pay with zero stock returns OutOfStock`() = runTest {
        setStock("classic-club", 0)
        val orderId = createAndSetDelivery("classic-club")

        expectPayError(orderId, "CARD")
    }

    // ====================================================
    //  Delivery fee calculation
    // ====================================================

    @Test
    fun `delivery under 500 has fee`() = runTest {
        val orderId = createOrder("Taras", listOf(OrderItemData("classic-club")))

        val result = setDelivery(orderId, "Test St 1", "+380991111111")

        assertEquals(50, result.deliveryFee)
    }

    @Test
    fun `delivery over 500 is free`() = runTest {
        val orderId = createOrder("Taras", (1..5).map { OrderItemData("classic-club") })

        val result = setDelivery(orderId, "Test St 1", "+380991111111")

        assertEquals(0, result.deliveryFee)
    }

    // ====================================================
    //  Wrong step order
    // ====================================================

    @Test
    fun `pay on DRAFT returns error`() = runTest {
        val orderId = createOrder("Taras", listOf(OrderItemData("classic-club")))

        expectPayError(orderId, "CARD")
    }

    @Test
    fun `dispatch on DRAFT returns error`() = runTest {
        val orderId = createOrder("Taras", listOf(OrderItemData("classic-club")))

        expectDispatchError(orderId)
    }

    @Test
    fun `complete on PREPARING returns error`() = runTest {
        val orderId = createDeliveryAndPay("classic-club")

        expectCompleteError(orderId)
    }

    @Test
    fun `delivery on already paid returns error`() = runTest {
        val orderId = createDeliveryAndPay("classic-club")

        expectSetDeliveryError(orderId, "new address", "+380991111111")
    }

    // ====================================================
    //  Invalid data
    // ====================================================

    @Test
    fun `create with empty items returns error`() = runTest {
        expectCreateOrderError("Taras", emptyList())
    }

    @Test
    fun `create with unknown sandwich returns error`() = runTest {
        expectCreateOrderError("Taras", listOf(OrderItemData("mega-burger")))
    }

    @Test
    fun `delivery with blank address returns error`() = runTest {
        val orderId = createOrder("Taras", listOf(OrderItemData("classic-club")))

        expectSetDeliveryError(orderId, "", "+380991234567")
    }

    @Test
    fun `get nonexistent order returns 404`() = runTest {
        expectOrderNotFound("nonexistent-id")
    }

    // ====================================================
    //  Menu
    // ====================================================

    @Test
    fun `get menu returns sandwiches and extras`() = runTest {
        expectMenuNotEmpty()
    }

    // ══════════════════════════════════════════════════════
    //  Abstract actions (happy path)
    // ══════════════════════════════════════════════════════

    abstract suspend fun createOrder(name: String, items: List<OrderItemData>): String
    abstract suspend fun setDelivery(orderId: String, address: String, phone: String): DeliverySetData
    abstract suspend fun payOrder(orderId: String, method: String)
    abstract suspend fun dispatchOrder(orderId: String)
    abstract suspend fun completeDelivery(orderId: String)
    abstract suspend fun cancelOrder(orderId: String): CancelledOrderData

    // ══════════════════════════════════════════════════════
    //  Abstract assertions
    // ══════════════════════════════════════════════════════

    abstract suspend fun expectOrderStatus(orderId: String, expected: String)
    abstract suspend fun expectOrderHasDelivery(orderId: String)
    abstract suspend fun expectOrderHasPayment(orderId: String)
    abstract suspend fun expectOrderNotFound(orderId: String)
    abstract suspend fun expectMenuNotEmpty()

    // ══════════════════════════════════════════════════════
    //  Abstract error expectations
    // ══════════════════════════════════════════════════════

    abstract suspend fun expectCreateOrderError(name: String, items: List<OrderItemData>)
    abstract suspend fun expectSetDeliveryError(orderId: String, address: String, phone: String)
    abstract suspend fun expectPayError(orderId: String, method: String)
    abstract suspend fun expectDispatchError(orderId: String)
    abstract suspend fun expectCompleteError(orderId: String)
    abstract suspend fun expectCancelError(orderId: String)

    // ══════════════════════════════════════════════════════
    //  Abstract stock management
    // ══════════════════════════════════════════════════════

    abstract suspend fun getStock(sandwichId: String): Int
    abstract suspend fun setStock(sandwichId: String, quantity: Int)

    // ══════════════════════════════════════════════════════
    //  Composable helpers (built from abstract actions)
    // ══════════════════════════════════════════════════════

    suspend fun createAndSetDelivery(vararg sandwichIds: String): String {
        val items = sandwichIds.map { OrderItemData(it) }
        val orderId = createOrder("Taras", items)
        setDelivery(orderId, "Khreshchatyk 1", "+380991234567")
        return orderId
    }

    suspend fun createDeliveryAndPay(vararg sandwichIds: String): String {
        val orderId = createAndSetDelivery(*sandwichIds)
        payOrder(orderId, "CARD")
        return orderId
    }
}

// ── Data classes (transport-agnostic) ──

data class OrderItemData(val sandwichId: String, val extras: List<String> = emptyList())
data class DeliverySetData(val deliveryFee: Int, val total: Int)
data class CancelledOrderData(val refund: Boolean)
