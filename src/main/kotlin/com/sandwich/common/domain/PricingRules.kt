package com.sandwich.common.domain

// ── Усі функції тут ЧИСТІ: fun (не suspend), без IO, без side effects ──

const val MAX_ITEMS_PER_ORDER = 10
const val MAX_EXTRAS_PER_SANDWICH = 5
const val BULK_DISCOUNT_THRESHOLD = 3       // від 3 сендвічів — знижка
const val BULK_DISCOUNT_PERCENT = 10        // 10%
const val DELIVERY_BASE_FEE = 50            // базова доставка, грн
const val FREE_DELIVERY_THRESHOLD = 500     // безкоштовна доставка від, грн

/**
 * Порахувати вартість одного рядка замовлення:
 * ціна сендвіча + сума всіх extras.
 */
fun calculateLineTotal(sandwichPrice: Int, extraPrices: List<Int>): Int =
    sandwichPrice + extraPrices.sum()

/**
 * Знижка: 10% від subtotal якщо >= 3 сендвічі.
 */
fun calculateDiscount(itemCount: Int, subtotal: Int): Int =
    if (itemCount >= BULK_DISCOUNT_THRESHOLD) subtotal * BULK_DISCOUNT_PERCENT / 100
    else 0

/**
 * Вартість доставки: безкоштовно від 500 грн, інакше 50 грн.
 */
fun calculateDeliveryFee(orderSubtotal: Int): Int =
    if (orderSubtotal >= FREE_DELIVERY_THRESHOLD) 0 else DELIVERY_BASE_FEE
