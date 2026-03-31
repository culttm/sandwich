package com.sandwich.common.database.bson

import org.bson.codecs.pojo.annotations.BsonId

// ══════════════════════════════════════════════════════════════
//  BSON data class for "stock" collection
// ══════════════════════════════════════════════════════════════

data class StockEntryBson(
    @BsonId val id: String,
    val quantity: Int
)
