package com.sandwich.common.database.collection.order

import com.sandwich.common.database.bson.OrderBson
import com.sandwich.common.database.bson.toBson
import com.sandwich.features.Order
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoCollection

class OptimisticLockException(message: String) : RuntimeException(message)

suspend fun MongoCollection<OrderBson>.saveOrder(order: Order) {
    val bson = order.toBson()

    if (bson.version == 0) {
        val toInsert = bson.copy(version = 1)
        insertOne(toInsert)
    } else {
        val result = findOneAndReplace(
            Filters.and(
                Filters.eq("_id", order.id),
                Filters.eq("version", order.version)
            ),
            bson.copy(version = bson.version + 1),
            FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)
        )
        if (result == null) {
            throw OptimisticLockException(
                "Order ${order.id} was modified (expected version ${order.version})"
            )
        }
    }
}
