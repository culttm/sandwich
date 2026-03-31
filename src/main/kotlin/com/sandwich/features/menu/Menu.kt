package com.sandwich.features.menu

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.MenuItem
import kotlinx.serialization.Serializable

@Serializable
data class Menu(
    val sandwiches: List<MenuItem>,
    val extras: List<ExtraItem>
)
