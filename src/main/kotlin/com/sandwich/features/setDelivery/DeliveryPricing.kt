package com.sandwich.features.setDelivery

// ── Усі функції тут ЧИСТІ: fun (не suspend), без IO, без side effects ──

const val DELIVERY_BASE_FEE = 50
const val FREE_DELIVERY_THRESHOLD = 500

/**
 * Вартість доставки: безкоштовно від 500 грн, інакше 50 грн.
 */
fun calculateDeliveryFee(orderSubtotal: Int): Int =
    if (orderSubtotal >= FREE_DELIVERY_THRESHOLD) 0 else DELIVERY_BASE_FEE
