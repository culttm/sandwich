package com.sandwich.features.orders

import com.sandwich.common.http.configureErrorHandling
import com.sandwich.common.http.configureRouting
import com.sandwich.common.http.configureSerialization
import com.sandwich.common.infra.Db
import com.sandwich.common.infra.seed
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderFlowE2ETest {

    private fun ApplicationTestBuilder.setup(): Db {
        val db = Db().apply { seed() }
        application {
            configureSerialization()
            configureErrorHandling()
            configureRouting(db)
        }
        return db
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    // ====================================================
    //  Happy path: full order lifecycle
    // ====================================================

    @Test
    fun `full flow - create, delivery, pay, dispatch, complete`() = testApplication {
        setup()
        val client = jsonClient()

        // 1. Create order -> DRAFT
        val createResponse = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Taras","items":[{"sandwichId":"classic-club"},{"sandwichId":"blt","extras":["bacon"]}]}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val orderId = createBody["orderId"]!!.jsonPrimitive.content
        assertNotNull(orderId)

        // Verify DRAFT
        val draft = client.get("/orders/$orderId")
        assertEquals(HttpStatusCode.OK, draft.status)
        val draftBody = Json.parseToJsonElement(draft.bodyAsText()).jsonObject
        assertEquals("DRAFT", draftBody["status"]!!.jsonPrimitive.content)

        // 2. Set delivery -> AWAITING_PAYMENT
        val deliveryResponse = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"Khreshchatyk 1, Kyiv","phone":"+380991234567"}""")
        }
        assertEquals(HttpStatusCode.OK, deliveryResponse.status)
        val deliveryBody = Json.parseToJsonElement(deliveryResponse.bodyAsText()).jsonObject
        assertTrue(deliveryBody.containsKey("deliveryFee"))
        assertTrue(deliveryBody.containsKey("total"))

        val awaiting = client.get("/orders/$orderId")
        val awaitingBody = Json.parseToJsonElement(awaiting.bodyAsText()).jsonObject
        assertEquals("AWAITING_PAYMENT", awaitingBody["status"]!!.jsonPrimitive.content)
        assertNotNull(awaitingBody["delivery"])

        // 3. Pay -> PREPARING (stock reserved)
        val payResponse = client.post("/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"CARD"}""")
        }
        assertEquals(HttpStatusCode.OK, payResponse.status)
        val payBody = Json.parseToJsonElement(payResponse.bodyAsText()).jsonObject
        assertEquals("PREPARING", payBody["status"]!!.jsonPrimitive.content)

        val preparing = client.get("/orders/$orderId")
        val preparingBody = Json.parseToJsonElement(preparing.bodyAsText()).jsonObject
        assertEquals("PREPARING", preparingBody["status"]!!.jsonPrimitive.content)
        assertNotNull(preparingBody["payment"])

        // 4. Dispatch -> OUT_FOR_DELIVERY
        val dispatchResponse = client.post("/orders/$orderId/dispatch")
        assertEquals(HttpStatusCode.OK, dispatchResponse.status)

        val dispatched = client.get("/orders/$orderId")
        val dispatchedBody = Json.parseToJsonElement(dispatched.bodyAsText()).jsonObject
        assertEquals("OUT_FOR_DELIVERY", dispatchedBody["status"]!!.jsonPrimitive.content)

        // 5. Complete -> DELIVERED
        val completeResponse = client.post("/orders/$orderId/complete")
        assertEquals(HttpStatusCode.OK, completeResponse.status)

        val delivered = client.get("/orders/$orderId")
        val deliveredBody = Json.parseToJsonElement(delivered.bodyAsText()).jsonObject
        assertEquals("DELIVERED", deliveredBody["status"]!!.jsonPrimitive.content)
    }

    // ====================================================
    //  Cancellation at different stages
    // ====================================================

    @Test
    fun `cancel DRAFT - cancelled without refund`() = testApplication {
        setup()
        val client = jsonClient()

        val createResponse = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Olya","items":[{"sandwichId":"veggie-delight"}]}""")
        }
        val orderId = Json.parseToJsonElement(createResponse.bodyAsText())
            .jsonObject["orderId"]!!.jsonPrimitive.content

        val cancelResponse = client.post("/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val cancelBody = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertEquals("CANCELLED", cancelBody["status"]!!.jsonPrimitive.content)
        assertEquals(false, cancelBody["refund"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `cancel AWAITING_PAYMENT - cancelled without refund`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createAndSetDelivery(client)

        val cancelResponse = client.post("/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertEquals(false, body["refund"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `cancel PREPARING - cancelled with refund`() = testApplication {
        val db = setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val stockBefore = db.stock["classic-club"]!!

        val cancelResponse = client.post("/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertEquals(true, body["refund"]!!.jsonPrimitive.boolean)

        // Stock restored
        assertEquals(stockBefore + 1, db.stock["classic-club"])
    }

    @Test
    fun `cancel OUT_FOR_DELIVERY - too late`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)
        client.post("/orders/$orderId/dispatch")

        val cancelResponse = client.post("/orders/$orderId/cancel")
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    // ====================================================
    //  Stock reservation
    // ====================================================

    @Test
    fun `pay decreases stock`() = testApplication {
        val db = setup()
        val client = jsonClient()

        val stockBefore = db.stock["classic-club"]!!

        createDeliveryAndPay(client)

        assertEquals(stockBefore - 1, db.stock["classic-club"])
    }

    @Test
    fun `pay with zero stock returns OutOfStock`() = testApplication {
        val db = setup()
        val client = jsonClient()

        db.stock["classic-club"] = 0

        val orderId = createAndSetDelivery(client)

        val payResponse = client.post("/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"CARD"}""")
        }
        val body = Json.parseToJsonElement(payResponse.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    // ====================================================
    //  Delivery fee calculation
    // ====================================================

    @Test
    fun `delivery under 500 has fee`() = testApplication {
        setup()
        val client = jsonClient()

        // classic-club = 120 < 500
        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"Test St 1","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(50, body["deliveryFee"]!!.jsonPrimitive.int)
    }

    @Test
    fun `delivery over 500 is free`() = testApplication {
        setup()
        val client = jsonClient()

        // 5 x classic-club = 600, with 10% discount subtotal=600 -> deliveryFee from subtotal >= 500 -> free
        val orderId = createOrder(client, "classic-club", "classic-club", "classic-club", "classic-club", "classic-club")

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"Test St 1","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["deliveryFee"]!!.jsonPrimitive.int)
    }

    // ====================================================
    //  Wrong step order
    // ====================================================

    @Test
    fun `pay on DRAFT returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"CARD"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `dispatch on DRAFT returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/dispatch")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `complete on PREPARING returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val response = client.post("/orders/$orderId/complete")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `delivery on already paid returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"new address","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    // ====================================================
    //  Invalid data
    // ====================================================

    @Test
    fun `create with empty items returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Taras","items":[]}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `create with unknown sandwich returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Taras","items":[{"sandwichId":"mega-burger"}]}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `delivery with blank address returns error`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"","phone":"+380991234567"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `get nonexistent order returns 404`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.get("/orders/nonexistent-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ====================================================
    //  Menu
    // ====================================================

    @Test
    fun `get menu returns sandwiches and extras`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.get("/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["sandwiches"]!!.jsonArray.isNotEmpty())
        assertTrue(body["extras"]!!.jsonArray.isNotEmpty())
    }

    // ====================================================
    //  Helpers
    // ====================================================

    private suspend fun createOrder(client: io.ktor.client.HttpClient, vararg sandwichIds: String): String {
        val items = sandwichIds.joinToString(",") { """{"sandwichId":"$it"}""" }
        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Taras","items":[$items]}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["orderId"]!!.jsonPrimitive.content
    }

    private suspend fun createAndSetDelivery(client: io.ktor.client.HttpClient): String {
        val orderId = createOrder(client, "classic-club")
        client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"Khreshchatyk 1","phone":"+380991234567"}""")
        }
        return orderId
    }

    private suspend fun createDeliveryAndPay(client: io.ktor.client.HttpClient): String {
        val orderId = createAndSetDelivery(client)
        client.post("/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"CARD"}""")
        }
        return orderId
    }
}
