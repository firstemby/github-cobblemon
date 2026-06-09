package com.cobblemon.mod.common.api.storage.player.adapter

import com.cobblemon.mod.common.api.pokedex.scan.ScannedBlocksManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.util.adapters.CodecBackedAdapter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mongodb.client.MongoClient
import java.util.UUID

class ScannedBlocksMongoBackend(
    mongoClient: MongoClient,
    databaseName: String,
    collectionName: String
) : MongoBackedPlayerDataStoreBackend<ScannedBlocksManager>(
    mongoClient,
    databaseName,
    collectionName,
    PlayerInstancedDataStoreTypes.SCANNED_BLOCKS
) {
    override val gson = GsonBuilder()
        .registerTypeAdapter(ScannedBlocksManager::class.java, CodecBackedAdapter(ScannedBlocksManager.CODEC))
        .create()
    override val classToken = TypeToken.get(ScannedBlocksManager::class.java)
    override val defaultData = { uuid: UUID -> ScannedBlocksManager(uuid) }

    override fun initialize(store: ScannedBlocksManager) {}
}
