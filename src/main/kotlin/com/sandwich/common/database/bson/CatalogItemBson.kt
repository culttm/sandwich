package com.sandwich.common.database.bson

import com.sandwich.features.CatalogItem
import org.bson.codecs.pojo.annotations.BsonId

// ══════════════════════════════════════════════════════════════
//  BSON data class for "catalog_items" collection
// ══════════════════════════════════════════════════════════════

data class CatalogItemBson(
    @BsonId val id: String,
    val name: String,
    val price: Int,
    val category: String = ""
) {
    fun toDomain() = CatalogItem(id = id, name = name, price = price, category = category)
}

fun CatalogItem.toBson() = CatalogItemBson(
    id = id,
    name = name,
    price = price,
    category = category
)
