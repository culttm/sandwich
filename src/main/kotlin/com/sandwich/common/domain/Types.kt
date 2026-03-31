package com.sandwich.common.domain

import kotlinx.serialization.Serializable

// ── Catalog value types (shared across features) ──

@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val price: Int
)

@Serializable
data class ExtraItem(
    val id: String,
    val name: String,
    val price: Int
)
