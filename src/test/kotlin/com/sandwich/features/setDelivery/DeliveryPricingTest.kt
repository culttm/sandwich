package com.sandwich.features.setDelivery

import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryPricingTest {

    @Test
    fun `deliveryFee under 500 is paid`() {
        assertEquals(50, calculateDeliveryFee(230))
    }

    @Test
    fun `deliveryFee at 500 is free`() {
        assertEquals(0, calculateDeliveryFee(500))
    }

    @Test
    fun `deliveryFee over 500 is free`() {
        assertEquals(0, calculateDeliveryFee(600))
    }
}
