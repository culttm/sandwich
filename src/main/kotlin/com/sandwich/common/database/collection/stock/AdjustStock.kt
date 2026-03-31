package com.sandwich.common.database.collection.stock

import com.sandwich.common.database.bson.StockEntryBson
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection

/**
 * Atomic stock adjustment using $inc operator — no read-then-write race condition.
 */
suspend fun MongoCollection<StockEntryBson>.adjustStock(id: String, delta: Int) {
    updateOne(
        Filters.eq("_id", id),
        Updates.inc("quantity", delta),
        UpdateOptions().upsert(true)
    )
}

/**
 * Set stock to exact quantity (for tests / seed data).
 */
suspend fun MongoCollection<StockEntryBson>.setStock(id: String, quantity: Int) {
    replaceOne(
        Filters.eq("_id", id),
        StockEntryBson(id = id, quantity = quantity),
        ReplaceOptions().upsert(true)
    )
}

suspend fun MongoCollection<StockEntryBson>.saveStockEntries(vararg entries: Pair<String, Int>) {
    entries.forEach { (id, quantity) -> setStock(id, quantity) }
}
