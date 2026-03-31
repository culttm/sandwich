package com.sandwich.features.menu

import kotlinx.serialization.Serializable

// ── Catalog types (owned by menu, referenced by other features) ──

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

@Serializable
data class Menu(
    val sandwiches: List<MenuItem>,
    val extras: List<ExtraItem>
)
