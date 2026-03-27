package com.sandwich.common.infra

import com.sandwich.common.domain.ExtraItem
import com.sandwich.common.domain.Menu
import com.sandwich.common.domain.MenuItem

/**
 * In-memory меню. В реальному сервісі — з БД.
 * Impure: мутабельний стан (хоча тут статичний).
 */
class MenuStore {
    private val sandwiches = listOf(
        MenuItem("classic-club",    "Classic Club",    120),
        MenuItem("turkey-avocado",  "Turkey Avocado",  145),
        MenuItem("veggie-delight",  "Veggie Delight",   99),
        MenuItem("blt",             "BLT",             110),
    )

    private val extras = listOf(
        ExtraItem("extra-cheese",  "Сир додатковий",  25),
        ExtraItem("jalapenos",     "Халапеньо",        15),
        ExtraItem("bacon",         "Бекон",            35),
        ExtraItem("avocado",       "Авокадо",          30),
        ExtraItem("extra-sauce",   "Соус додатковий",  10),
    )

    fun getMenu(): Menu = Menu(sandwiches, extras)

    fun getSandwichMap(): Map<String, MenuItem> =
        sandwiches.associateBy { it.id }

    fun getExtrasMap(): Map<String, ExtraItem> =
        extras.associateBy { it.id }
}
