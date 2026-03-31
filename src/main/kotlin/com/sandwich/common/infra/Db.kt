package com.sandwich.common.infra

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.sandwich.features.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider

/**
 * MongoDB-backed store.
 * Кожний слайс отримує Db і робить свої запити через нього.
 */
class Db(private val database: MongoDatabase) {

    private val catalogItems = database.getCollection<CatalogItem>("catalog_items")
    private val orders = database.getCollection<Order>("orders")
    private val stockDocs = database.getCollection<StockEntry>("stock")

    // ── Catalog ──

    suspend fun allSandwiches(): Map<String, CatalogItem> =
        catalogItems.find(Filters.eq("category", "sandwich")).toList().associateBy { it.id }

    suspend fun allExtras(): Map<String, CatalogItem> =
        catalogItems.find(Filters.eq("category", "extra")).toList().associateBy { it.id }

    // ── Orders ──

    suspend fun findOrder(id: String): Order? =
        orders.find(Filters.eq("_id", id)).firstOrNull()

    suspend fun saveOrder(order: Order) {
        orders.replaceOne(
            Filters.eq("_id", order.id),
            order,
            ReplaceOptions().upsert(true)
        )
    }

    // ── Stock ──

    suspend fun allStock(): Map<String, Int> =
        stockDocs.find().toList().associate { it.id to it.quantity }

    suspend fun getStock(sandwichId: String): Int =
        stockDocs.find(Filters.eq("_id", sandwichId)).firstOrNull()?.quantity ?: 0

    suspend fun adjustStock(sandwichId: String, delta: Int) {
        val current = getStock(sandwichId)
        stockDocs.replaceOne(
            Filters.eq("_id", sandwichId),
            StockEntry(sandwichId, current + delta),
            ReplaceOptions().upsert(true)
        )
    }

    // ── Seed helpers ──

    suspend fun saveCatalogItems(vararg items: CatalogItem) {
        items.forEach { item ->
            catalogItems.replaceOne(
                Filters.eq("_id", item.id),
                item,
                ReplaceOptions().upsert(true)
            )
        }
    }

    suspend fun saveStockEntries(vararg entries: StockEntry) {
        entries.forEach { entry ->
            stockDocs.replaceOne(
                Filters.eq("_id", entry.id),
                entry,
                ReplaceOptions().upsert(true)
            )
        }
    }

    companion object {
        fun create(connectionString: String, databaseName: String = "sandwich"): Db {
            val codecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
                CodecRegistries.fromProviders(KotlinSerializerCodecProvider()),
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry()
            )
            val client = MongoClient.create(connectionString)
            val database = client.getDatabase(databaseName).withCodecRegistry(codecRegistry)
            return Db(database)
        }
    }
}

@kotlinx.serialization.Serializable
data class StockEntry(
    @kotlinx.serialization.SerialName("_id")
    val id: String,
    val quantity: Int
)
