package com.sandwich

import org.testcontainers.containers.MongoDBContainer

class TestDatabase {
    companion object {
        val db by lazy {
            MongoDBContainer("mongo:7").also {
                it.start()
            }
        }

        val connectionString by lazy { db.connectionString }
    }
}