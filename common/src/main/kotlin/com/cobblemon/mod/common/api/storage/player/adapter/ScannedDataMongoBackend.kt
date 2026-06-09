package com.cobblemon.mod.common.api.storage.player.adapter

import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.util.adapters.CodecBackedAdapter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mongodb.client.MongoClient
import java.util.UUID

class ScannedDataMongoBackend(
    mongoClient: MongoClient,
    databaseName: String,
    collectionName: String
) : MongoBackedPlayerDataStoreBackend<ScannedDataManager>(
    mongoClient,
    databaseName,
    collectionName,
    PlayerInstancedDataStoreTypes.SCANNED_DATA
) {
    override val gson = GsonBuilder()
        .registerTypeAdapter(ScannedDataManager::class.java, CodecBackedAdapter(ScannedDataManager.CODEC))
        .create()
    override val classToken = TypeToken.get(ScannedDataManager::class.java)
    override val defaultData = { uuid: UUID -> ScannedDataManager(uuid) }

    override fun initialize(store: ScannedDataManager) {}
}
