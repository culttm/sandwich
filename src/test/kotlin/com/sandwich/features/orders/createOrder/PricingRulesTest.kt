package com.sandwich.features.orders.createOrder

import kotlin.test.Test
import kotlin.test.assertEquals

class PricingRulesTest {

    @Test
    fun `lineTotal sandwich without extras`() {
        assertEquals(120, calculateLineTotal(120, emptyList()))
    }

    @Test
    fun `lineTotal sandwich with extras`() {
        assertEquals(170, calculateLineTotal(110, listOf(25, 35)))
    }

    @Test
    fun `discount less than 3 items no discount`() {
        assertEquals(0, calculateDiscount(2, 230))
    }

    @Test
    fun `discount exactly 3 items 10 percent`() {
        assertEquals(32, calculateDiscount(3, 329))
    }

    @Test
    fun `discount 5 items 10 percent`() {
        assertEquals(55, calculateDiscount(5, 550))
    }
}
