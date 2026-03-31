package com.sandwich.features.orders.createOrder

// ── Усі функції тут ЧИСТІ: fun (не suspend), без IO, без side effects ──

const val MAX_ITEMS_PER_ORDER = 10
const val MAX_EXTRAS_PER_SANDWICH = 5
const val BULK_DISCOUNT_THRESHOLD = 3
const val BULK_DISCOUNT_PERCENT = 10

/**
 * Вартість одного рядка замовлення:
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
