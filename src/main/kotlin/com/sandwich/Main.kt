package com.sandwich

import com.sandwich.apps.SandwichHttpApi
import com.sandwich.apps.seed
import com.sandwich.common.app.run
import com.mongodb.kotlin.client.coroutine.MongoClient

suspend fun main(args: Array<String> = emptyArray()) {
    val mongoUri = System.getenv("MONGO_URI") ?: "mongodb://localhost:27017"
    val client = MongoClient.create(mongoUri)
    val database = client.getDatabase("sandwich")
    database.seed()

    when(args.firstOrNull()) {
        else -> SandwichHttpApi(database).run()
    }
}
