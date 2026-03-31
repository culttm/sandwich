package com.sandwich.common.database.bson

import com.sandwich.features.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty

// ══════════════════════════════════════════════════════════════
//  BSON data classes for "orders" collection
//  Domain ↔ BSON mapping is explicit via toDomain() / toBson()
// ══════════════════════════════════════════════════════════════

data class OrderBson(
    @BsonId val id: String,
    @BsonProperty("customer_name") val customerName: String,
    val items: List<OrderLineBson>,
    val subtotal: Int,
    val discount: Int,
    @BsonProperty("delivery_fee") val deliveryFee: Int = 0,
    val total: Int,
    val status: String,
    val delivery: DeliveryInfoBson? = null,
    val payment: PaymentInfoBson? = null,
    @BsonProperty("created_at") val createdAt: String,
    val version: Int = 0
) {
    fun toDomain() = Order(
        id = id,
        customerName = customerName,
        items = items.map { it.toDomain() },
        subtotal = subtotal,
        discount = discount,
        deliveryFee = deliveryFee,
        total = total,
        status = OrderStatus.valueOf(status),
        delivery = delivery?.toDomain(),
        payment = payment?.toDomain(),
        createdAt = createdAt,
        version = version
    )
}

fun Order.toBson() = OrderBson(
    id = id,
    customerName = customerName,
    items = items.map { it.toBson() },
    subtotal = subtotal,
    discount = discount,
    deliveryFee = deliveryFee,
    total = total,
    status = status.name,
    delivery = delivery?.toBson(),
    payment = payment?.toBson(),
    createdAt = createdAt,
    version = version
)

// ── OrderLine ──

data class OrderLineBson(
    @BsonProperty("sandwich_id") val sandwichId: String,
    @BsonProperty("sandwich_name") val sandwichName: String,
    @BsonProperty("sandwich_price") val sandwichPrice: Int,
    val extras: List<ExtraBson>,
    @BsonProperty("line_total") val lineTotal: Int
) {
    fun toDomain() = OrderLine(
        sandwichId = sandwichId,
        sandwichName = sandwichName,
        sandwichPrice = sandwichPrice,
        extras = extras.map { it.toDomain() },
        lineTotal = lineTotal
    )
}

fun OrderLine.toBson() = OrderLineBson(
    sandwichId = sandwichId,
    sandwichName = sandwichName,
    sandwichPrice = sandwichPrice,
    extras = extras.map { it.toExtraBson() },
    lineTotal = lineTotal
)

// ── Extra (embedded in OrderLine) ──

data class ExtraBson(
    val id: String,
    val name: String,
    val price: Int,
    val category: String = ""
) {
    fun toDomain() = CatalogItem(id = id, name = name, price = price, category = category)
}

fun CatalogItem.toExtraBson() = ExtraBson(id = id, name = name, price = price, category = category)

// ── DeliveryInfo ──

data class DeliveryInfoBson(
    val address: String,
    val phone: String,
    @BsonProperty("delivery_time") val deliveryTime: String?,
    @BsonProperty("delivery_fee") val deliveryFee: Int
) {
    fun toDomain() = DeliveryInfo(
        address = address,
        phone = phone,
        deliveryTime = deliveryTime,
        deliveryFee = deliveryFee
    )
}

fun DeliveryInfo.toBson() = DeliveryInfoBson(
    address = address,
    phone = phone,
    deliveryTime = deliveryTime,
    deliveryFee = deliveryFee
)

// ── PaymentInfo ──

data class PaymentInfoBson(
    val method: String,
    @BsonProperty("paid_at") val paidAt: String,
    @BsonProperty("transaction_id") val transactionId: String?
) {
    fun toDomain() = PaymentInfo(
        method = PaymentMethod.valueOf(method),
        paidAt = paidAt,
        transactionId = transactionId
    )
}

fun PaymentInfo.toBson() = PaymentInfoBson(
    method = method.name,
    paidAt = paidAt,
    transactionId = transactionId
)
