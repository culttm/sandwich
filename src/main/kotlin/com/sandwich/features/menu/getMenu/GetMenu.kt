package com.sandwich.features.menu.getMenu

import com.sandwich.common.domain.Menu
import com.sandwich.common.infra.MenuStore

// ── Level 1: Direct Query ──
// Просте читання — без бізнес-логіки, без sandwich.

fun GetMenu(menuStore: MenuStore): suspend () -> Menu = {
    menuStore.getMenu()
}
