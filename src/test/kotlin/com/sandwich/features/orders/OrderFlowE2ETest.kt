package com.sandwich.features.orders

import com.sandwich.common.domain.OrderStatus
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

/**
 * E2E тести повного flow замовлення через HTTP.
 * Використовують Ktor testApplication — без реального порту, швидко, CI-friendly.
 */
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

    // ══════════════════════════════════════════════════════════
    //  Happy path: повний цикл замовлення
    // ══════════════════════════════════════════════════════════

    @Test
    fun `повний flow — create, delivery, pay, dispatch, complete`() = testApplication {
        setup()
        val client = jsonClient()

        // 1. Створити замовлення → DRAFT
        val createResponse = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Тарас","items":[{"sandwichId":"classic-club"},{"sandwichId":"blt","extras":["bacon"]}]}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createBody = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val orderId = createBody["orderId"]!!.jsonPrimitive.content
        assertNotNull(orderId)

        // Перевірити що DRAFT
        val draft = client.get("/orders/$orderId")
        assertEquals(HttpStatusCode.OK, draft.status)
        val draftBody = Json.parseToJsonElement(draft.bodyAsText()).jsonObject
        assertEquals("DRAFT", draftBody["status"]!!.jsonPrimitive.content)

        // 2. Вказати доставку → AWAITING_PAYMENT
        val deliveryResponse = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"вул. Хрещатик 1, Київ","phone":"+380991234567"}""")
        }
        assertEquals(HttpStatusCode.OK, deliveryResponse.status)
        val deliveryBody = Json.parseToJsonElement(deliveryResponse.bodyAsText()).jsonObject
        assertTrue(deliveryBody.containsKey("deliveryFee"))
        assertTrue(deliveryBody.containsKey("total"))

        val awaiting = client.get("/orders/$orderId")
        val awaitingBody = Json.parseToJsonElement(awaiting.bodyAsText()).jsonObject
        assertEquals("AWAITING_PAYMENT", awaitingBody["status"]!!.jsonPrimitive.content)
        assertNotNull(awaitingBody["delivery"])

        // 3. Оплата → PREPARING (+ stock зарезервовано)
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

        // 4. Відправити кур'єром → OUT_FOR_DELIVERY
        val dispatchResponse = client.post("/orders/$orderId/dispatch")
        assertEquals(HttpStatusCode.OK, dispatchResponse.status)

        val dispatched = client.get("/orders/$orderId")
        val dispatchedBody = Json.parseToJsonElement(dispatched.bodyAsText()).jsonObject
        assertEquals("OUT_FOR_DELIVERY", dispatchedBody["status"]!!.jsonPrimitive.content)

        // 5. Доставлено → DELIVERED
        val completeResponse = client.post("/orders/$orderId/complete")
        assertEquals(HttpStatusCode.OK, completeResponse.status)

        val delivered = client.get("/orders/$orderId")
        val deliveredBody = Json.parseToJsonElement(delivered.bodyAsText()).jsonObject
        assertEquals("DELIVERED", deliveredBody["status"]!!.jsonPrimitive.content)
    }

    // ══════════════════════════════════════════════════════════
    //  Скасування на різних етапах
    // ══════════════════════════════════════════════════════════

    @Test
    fun `cancel DRAFT — скасовується без refund`() = testApplication {
        setup()
        val client = jsonClient()

        val createResponse = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Оля","items":[{"sandwichId":"veggie-delight"}]}""")
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
    fun `cancel AWAITING_PAYMENT — скасовується без refund`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createAndSetDelivery(client)

        val cancelResponse = client.post("/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertEquals(false, body["refund"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `cancel PREPARING — скасовується з refund`() = testApplication {
        val db = setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val stockBefore = db.stock["classic-club"]!!

        val cancelResponse = client.post("/orders/$orderId/cancel")
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertEquals(true, body["refund"]!!.jsonPrimitive.boolean)

        // Stock повернувся
        assertEquals(stockBefore + 1, db.stock["classic-club"])
    }

    @Test
    fun `cancel OUT_FOR_DELIVERY — занадто пізно`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)
        client.post("/orders/$orderId/dispatch")

        val cancelResponse = client.post("/orders/$orderId/cancel")
        val body = Json.parseToJsonElement(cancelResponse.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    // ══════════════════════════════════════════════════════════
    //  Stock резервація
    // ══════════════════════════════════════════════════════════

    @Test
    fun `pay зменшує stock`() = testApplication {
        val db = setup()
        val client = jsonClient()

        val stockBefore = db.stock["classic-club"]!!

        createDeliveryAndPay(client)

        assertEquals(stockBefore - 1, db.stock["classic-club"])
    }

    @Test
    fun `pay при нульовому stock — OutOfStock`() = testApplication {
        val db = setup()
        val client = jsonClient()

        // Обнулити stock
        db.stock["classic-club"] = 0

        val orderId = createAndSetDelivery(client)

        val payResponse = client.post("/orders/$orderId/pay") {
            contentType(ContentType.Application.Json)
            setBody("""{"method":"CARD"}""")
        }
        val body = Json.parseToJsonElement(payResponse.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
        assertTrue(body["error"]!!.jsonPrimitive.content.contains("наявності"))
    }

    // ══════════════════════════════════════════════════════════
    //  Доставка — розрахунок вартості
    // ══════════════════════════════════════════════════════════

    @Test
    fun `доставка менше 500 грн — платна`() = testApplication {
        setup()
        val client = jsonClient()

        // classic-club = 120 грн < 500
        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"вул. Тест 1","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(50, body["deliveryFee"]!!.jsonPrimitive.int)
    }

    @Test
    fun `доставка від 500 грн — безкоштовна`() = testApplication {
        setup()
        val client = jsonClient()

        // 5 x classic-club = 600 грн, зі знижкою 10% subtotal = 600, знижка = 60 → total - discount = 540
        // Але deliveryFee рахується від subtotal (600) >= 500 → безкоштовно
        val orderId = createOrder(client, "classic-club", "classic-club", "classic-club", "classic-club", "classic-club")

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"вул. Тест 1","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["deliveryFee"]!!.jsonPrimitive.int)
    }

    // ══════════════════════════════════════════════════════════
    //  Порушення порядку кроків
    // ══════════════════════════════════════════════════════════

    @Test
    fun `pay на DRAFT — WrongStatus`() = testApplication {
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
    fun `dispatch на DRAFT — WrongStatus`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createOrder(client, "classic-club")

        val response = client.post("/orders/$orderId/dispatch")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `complete на PREPARING — WrongStatus`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val response = client.post("/orders/$orderId/complete")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `delivery на вже оплачене — WrongStatus`() = testApplication {
        setup()
        val client = jsonClient()

        val orderId = createDeliveryAndPay(client)

        val response = client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"нова адреса","phone":"+380991111111"}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    // ══════════════════════════════════════════════════════════
    //  Невалідні дані
    // ══════════════════════════════════════════════════════════

    @Test
    fun `create без items — помилка`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Тарас","items":[]}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `create з невідомим сендвічем — помилка`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Тарас","items":[{"sandwichId":"mega-burger"}]}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"))
    }

    @Test
    fun `delivery з порожньою адресою — помилка`() = testApplication {
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
    fun `get неіснуюче замовлення — 404`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.get("/orders/nonexistent-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ══════════════════════════════════════════════════════════
    //  Меню
    // ══════════════════════════════════════════════════════════

    @Test
    fun `get menu — повертає сендвічі та extras`() = testApplication {
        setup()
        val client = jsonClient()

        val response = client.get("/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["sandwiches"]!!.jsonArray.isNotEmpty())
        assertTrue(body["extras"]!!.jsonArray.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private suspend fun createOrder(client: io.ktor.client.HttpClient, vararg sandwichIds: String): String {
        val items = sandwichIds.joinToString(",") { """{"sandwichId":"$it"}""" }
        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"Тарас","items":[$items]}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["orderId"]!!.jsonPrimitive.content
    }

    private suspend fun createAndSetDelivery(client: io.ktor.client.HttpClient): String {
        val orderId = createOrder(client, "classic-club")
        client.post("/orders/$orderId/delivery") {
            contentType(ContentType.Application.Json)
            setBody("""{"address":"вул. Хрещатик 1","phone":"+380991234567"}""")
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
