package com.sandwich.common.database.collection.catalog

import com.sandwich.common.database.bson.CatalogItemBson
import com.sandwich.features.CatalogItem
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.toList

suspend fun MongoCollection<CatalogItemBson>.allByCategory(category: String): Map<String, CatalogItem> =
    find(Filters.eq("category", category))
        .toList()
        .map { it.toDomain() }
        .associateBy { it.id }
