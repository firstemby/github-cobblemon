/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import java.util.UUID
import net.minecraft.server.MinecraftServer
import org.bson.Document

class MongoEggStoreBackend(
    private val mongoClient: MongoClient,
    private val databaseName: String,
    private val collectionName: String = "EggCollection"
) : EggStoreBackend {

    private val collection by lazy {
        mongoClient.getDatabase(databaseName).getCollection(collectionName)
    }

    override fun setup(server: MinecraftServer) {
        // Ping the database to ensure it's connected
        mongoClient.listDatabaseNames().first()
    }

    override fun load(eggId: UUID): Pokemon? {
        val document = collection.find(Filters.eq("eggId", eggId.toString())).first() ?: return null
        return try {
            val jsonElement = JsonParser.parseString(document.getString("pokemon"))
            Pokemon.CODEC.decode(JsonOps.INSTANCE, jsonElement).getOrThrow {
                Cobblemon.LOGGER.error("Error decoding egg $eggId from MongoDB: $it")
                throw UnsupportedOperationException()
            }.first
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load egg $eggId from MongoDB", e)
            null
        }
    }

    override fun save(eggId: UUID, pokemon: Pokemon) {
        val jsonElement = Pokemon.CODEC.encodeStart(JsonOps.INSTANCE, pokemon).result().get()
        val doc = Document("eggId", eggId.toString())
            .append("pokemon", jsonElement.toString())
        collection.replaceOne(
            Filters.eq("eggId", eggId.toString()),
            doc,
            ReplaceOptions().upsert(true)
        )
    }

    override fun delete(eggId: UUID) {
        collection.deleteOne(Filters.eq("eggId", eggId.toString()))
    }
}
