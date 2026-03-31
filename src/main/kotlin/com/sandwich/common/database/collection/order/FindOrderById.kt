package com.sandwich.common.database.collection.order

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.features.Order
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull

suspend fun MongoCollection<OrderBson>.findOrderById(id: String): Order? =
    find(Filters.eq("_id", id)).firstOrNull()?.toDomain()
