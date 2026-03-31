package com.sandwich

import com.sandwich.apps.SandwichHttpApi
import com.sandwich.apps.seed
import com.sandwich.common.app.run
import com.sandwich.common.infra.Db

suspend fun main(args: Array<String> = emptyArray()) {
    val mongoUri = System.getenv("MONGO_URI") ?: "mongodb://localhost:27017"
    val db = Db.create(mongoUri).apply { seed() }

    when(args.firstOrNull()) {
        else -> SandwichHttpApi(db).run()
    }
}
