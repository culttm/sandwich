package com.sandwich.common.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тести чистих функцій ціноутворення.
 */
class PricingRulesTest {

    @Test
    fun `lineTotal — сендвіч без extras`() {
        assertEquals(120, calculateLineTotal(120, emptyList()))
    }

    @Test
    fun `lineTotal — сендвіч з extras`() {
        assertEquals(170, calculateLineTotal(110, listOf(25, 35)))
    }

    @Test
    fun `discount — менше 3 позицій, без знижки`() {
        assertEquals(0, calculateDiscount(2, 230))
    }

    @Test
    fun `discount — рівно 3 позиції, 10%`() {
        assertEquals(32, calculateDiscount(3, 329))
    }

    @Test
    fun `discount — 5 позицій, 10%`() {
        assertEquals(55, calculateDiscount(5, 550))
    }
}
