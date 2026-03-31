package com.sandwich.features

import com.sandwich.apps.SandwichHttpApi
import com.sandwich.apps.seed
import com.sandwich.common.database.bson.StockEntryBson
import com.sandwich.common.database.collection.stock.adjustStock
import com.sandwich.common.database.collection.stock.getStock
import com.sandwich.common.database.collection.stock.setStock
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ══════════════════════════════════════════════════════════════
//  E2E: запускаємо справжній SandwichHttpApi на :8080
//  MongoDB через TestContainers
// ══════════════════════════════════════════════════════════════

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFlowScenarioE2E : OrderFlowScenario() {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:7")
    }

    private lateinit var database: MongoDatabase
    private lateinit var teardown: () -> Unit
    private lateinit var client: HttpClient

    private val baseUrl = "http://localhost:8080"

    @BeforeEach
    fun setUp() {
        database = MongoClient.create(mongo.connectionString)
            .getDatabase("sandwich-test-${System.nanoTime()}")
        runBlocking { database.seed() }
        teardown = runBlocking { SandwichHttpApi(database)() }
        client = HttpClient {
            install(ContentNegotiation) { json() }
        }
    }

    @AfterEach
    fun tearDown() {
        client.close()
        teardown()
    }

    // ══════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════

    override suspend fun createOrder(name: String, items: List<OrderItemData>): String {
        val itemsJson = items.joinToString(",") { item ->
            val extrasJson = if (item.extras.isEmpty()) ""
            else ""","extras":[${item.extras.joinToString(",") { "\"$it\"" }}]"""
            """{"sandwichId":"${item.sandwichId}"$extrasJson}"""
        }
        val response = client.post("$baseUrl/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"$name","items":[$itemsJson]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["orderId"]!!.jsonPrimitive.content
    }

    override suspend fun setDelivery(orderId: String, address: String, phone: String): DeliverySetData {
        val response = client.post("$baseUrl/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"$address","phone":"$phone"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return DeliverySetData(
            deliveryFee = body["deliveryFee"]!!.jsonPrimitive.int,
            total = body["total"]!!.jsonPrimitive.int
        )
    }

    override suspend fun payOrder(orderId: String, method: String) {
        val response = client.post("$baseUrl/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"$method"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    override suspend fun dispatchOrder(orderId: String) {
        val response = client.post("$baseUrl/orders/$orderId/dispatch")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    override suspend fun completeDelivery(orderId: String) {
        val response = client.post("$baseUrl/orders/$orderId/complete")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    override suspend fun cancelOrder(orderId: String): CancelledOrderData {
        val response = client.post("$baseUrl/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return CancelledOrderData(
            refund = body["refund"]!!.jsonPrimitive.boolean
        )
    }

    // ══════════════════════════════════════════════════════
    //  Assertions
    // ══════════════════════════════════════════════════════

    override suspend fun expectOrderStatus(orderId: String, expected: String) {
        val response = client.get("$baseUrl/orders/$orderId")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expected, body["status"]!!.jsonPrimitive.content)
    }

    override suspend fun expectOrderHasDelivery(orderId: String) {
        val body = getOrderJson(orderId)
        val delivery = body["delivery"]
        assertNotNull(delivery)
        assertTrue(delivery !is JsonNull, "delivery should not be null")
    }

    override suspend fun expectOrderHasPayment(orderId: String) {
        val body = getOrderJson(orderId)
        val payment = body["payment"]
        assertNotNull(payment)
        assertTrue(payment !is JsonNull, "payment should not be null")
    }

    override suspend fun expectOrderNotFound(orderId: String) {
        val response = client.get("$baseUrl/orders/$orderId")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    override suspend fun expectMenuNotEmpty() {
        val response = client.get("$baseUrl/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["sandwiches"]!!.jsonArray.isNotEmpty())
        assertTrue(body["extras"]!!.jsonArray.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════
    //  Error expectations
    // ══════════════════════════════════════════════════════

    override suspend fun expectCreateOrderError(name: String, items: List<OrderItemData>) {
        val itemsJson = items.joinToString(",") { """{"sandwichId":"${it.sandwichId}"}""" }
        val response = client.post("$baseUrl/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"$name","items":[$itemsJson]}""")
        }
        assertHasError(response)
    }

    override suspend fun expectSetDeliveryError(orderId: String, address: String, phone: String) {
        val response = client.post("$baseUrl/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"$address","phone":"$phone"}""")
        }
        assertHasError(response)
    }

    override suspend fun expectPayError(orderId: String, method: String) {
        val response = client.post("$baseUrl/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"$method"}""")
        }
        assertHasError(response)
    }

    override suspend fun expectDispatchError(orderId: String) {
        val response = client.post("$baseUrl/orders/$orderId/dispatch")
        assertHasError(response)
    }

    override suspend fun expectCompleteError(orderId: String) {
        val response = client.post("$baseUrl/orders/$orderId/complete")
        assertHasError(response)
    }

    override suspend fun expectCancelError(orderId: String) {
        val response = client.post("$baseUrl/orders/$orderId/cancel")
        assertHasError(response)
    }

    // ══════════════════════════════════════════════════════
    //  Stock management (direct collection access)
    // ══════════════════════════════════════════════════════

    private val stockCollection get() = database.getCollection<StockEntryBson>("stock")

    override suspend fun getStock(sandwichId: String): Int =
        stockCollection.getStock(sandwichId)

    override suspend fun setStock(sandwichId: String, quantity: Int) {
        stockCollection.setStock(sandwichId, quantity)
    }

    // ══════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════

    private suspend fun getOrderJson(orderId: String): JsonObject {
        val response = client.get("$baseUrl/orders/$orderId")
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun assertHasError(response: HttpResponse) {
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(
            body.containsKey("error") || body.containsKey("code"),
            "Expected error in response: $body"
        )
    }
}
