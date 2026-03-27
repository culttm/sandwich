package com.sandwich

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
    fun `health check returns ok`() = withTestApp(SandwichHttpApi()) {

        val response = client.get("http://localhost:8080/health")

        val text = response.bodyAsText()

        println(text)
    }
}
