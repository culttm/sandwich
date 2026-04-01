package com.sandwich

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.sandwich.apps.SandwichHttpApi
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.test.Test

class AppTest {
    val client by lazy {
        HttpClient { expectSuccess = true }
    }

    @Test
    fun `health check returns ok`() {
        val database = MongoClient.create(TestDatabase.connectionString)
            .getDatabase("app-test-${System.nanoTime()}")
        withTestApp(SandwichHttpApi(database)) {
            val response = client.get("http://localhost:8080/health")
            val text = response.bodyAsText()
            println(text)
        }
    }
}
