package com.sandwich.features.menu.getMenu

import com.sandwich.common.domain.Menu
import com.sandwich.common.infra.Db

// ── Level 1: Direct Query ──

fun GetMenu(db: Db): suspend () -> Menu = {
    Menu(
        sandwiches = db.sandwiches.values.toList(),
        extras = db.extras.values.toList()
    )
}
