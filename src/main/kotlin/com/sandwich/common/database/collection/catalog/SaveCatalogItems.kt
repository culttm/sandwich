package com.sandwich.common.database.collection.catalog

import com.sandwich.common.database.bson.CatalogItemBson
import com.sandwich.common.database.bson.toBson
import com.sandwich.features.CatalogItem
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection

suspend fun MongoCollection<CatalogItemBson>.saveCatalogItems(vararg items: CatalogItem) {
    items.forEach { item ->
        replaceOne(
            Filters.eq("_id", item.id),
            item.toBson(),
            ReplaceOptions().upsert(true)
        )
    }
}
