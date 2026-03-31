package com.sandwich

import com.sandwich.apps.SandwichHttpApi
import com.sandwich.apps.seed
import com.sandwich.common.infra.Db
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test

@Testcontainers
class AppTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7")
    }

    val client by lazy {
        HttpClient { expectSuccess = true }
    }

    @Test
    fun `health check returns ok`() {
        val db = Db.create(mongo.connectionString, "app-test-${System.nanoTime()}")
        withTestApp(SandwichHttpApi(db)) {
            val response = client.get("http://localhost:8080/health")
            val text = response.bodyAsText()
            println(text)
        }
    }
}
