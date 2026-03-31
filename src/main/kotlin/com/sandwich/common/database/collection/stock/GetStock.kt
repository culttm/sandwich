package com.sandwich.common.database.collection.stock

import com.sandwich.common.database.bson.StockEntryBson
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

suspend fun MongoCollection<StockEntryBson>.getStock(id: String): Int =
    find(Filters.eq("_id", id)).firstOrNull()?.quantity ?: 0

suspend fun MongoCollection<StockEntryBson>.allStock(): Map<String, Int> =
    find().toList().associate { it.id to it.quantity }
