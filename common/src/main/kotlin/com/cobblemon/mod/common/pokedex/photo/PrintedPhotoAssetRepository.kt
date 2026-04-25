/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.config.CobblemonConfig
import com.google.gson.annotations.SerializedName
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.max

/**
 * Server-side, world-scoped, deduplicated storage for printed-photo image assets.
 *
 * Assets are written once per content hash (sha-256) and assigned a monotonic integer asset id.
 * Item components reference an asset by id; clients fetch the bytes on demand.
 */
object PrintedPhotoAssetRepository {
    private const val INDEX_FILE_NAME = "index.json"
    private const val BLOBS_DIRECTORY = "blobs"
    private const val ASSET_EXTENSION = "jpg"

    private val lock = Any()

    private var rootPath: Path? = null
    private var dirty = false
    private var nextAssetId: Int = 1
    private val assetById = mutableMapOf<Int, AssetRecord>()
    private val idByHash = mutableMapOf<String, Int>()

    data class AssetRecord(
        val assetId: Int,
        val hash: String,
        val width: Int,
        val height: Int,
        val createdAt: Long,
        val byteSize: Int
    )

    fun initialize(server: MinecraftServer) {
        synchronized(lock) {
            val root = server.getWorldPath(LevelResource.ROOT).resolve("printed_photo_assets")
            rootPath = root
            Files.createDirectories(root)
            Files.createDirectories(root.resolve(BLOBS_DIRECTORY))

            nextAssetId = 1
            assetById.clear()
            idByHash.clear()
            dirty = false

            loadIndex(root)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            if (dirty) rootPath?.let(::saveIndex)
            rootPath = null
            assetById.clear()
            idByHash.clear()
            dirty = false
        }
    }

    /**
     * Returns the existing asset record for [jpegBytes] if its hash is already stored,
     * otherwise writes the bytes to disk under a new asset id and returns the new record.
     */
    fun createOrGetAssetFromJpeg(jpegBytes: ByteArray, width: Int, height: Int): AssetRecord? {
        synchronized(lock) {
            val root = rootPath ?: return null
            val hash = computeHash(jpegBytes)
            idByHash[hash]?.let { existingId ->
                return assetById[existingId]
            }

            val blobFile = blobPath(root, hash)
            if (!Files.exists(blobFile)) {
                val written = runCatching {
                    Files.createDirectories(blobFile.parent)
                    Files.write(blobFile, jpegBytes)
                }.onFailure {
                    Cobblemon.LOGGER.warn("Failed to write printed photo asset blob {}", hash, it)
                }.isSuccess
                if (!written) return null
            }

            val assetId = nextAssetId++
            val record = AssetRecord(
                assetId = assetId,
                hash = hash,
                width = width,
                height = height,
                createdAt = System.currentTimeMillis(),
                byteSize = jpegBytes.size
            )
            assetById[assetId] = record
            idByHash[hash] = assetId
            dirty = true
            saveIndex(root)
            return record
        }
    }

    fun getAssetBytes(assetId: Int): ByteArray? {
        synchronized(lock) {
            val root = rootPath ?: return null
            val record = assetById[assetId] ?: return null
            val path = blobPath(root, record.hash)
            if (!Files.exists(path)) return null
            return runCatching { Files.readAllBytes(path) }
                .onFailure { Cobblemon.LOGGER.warn("Failed to read printed photo asset blob {}", record.hash, it) }
                .getOrNull()
        }
    }

    fun getAssetRecord(assetId: Int): AssetRecord? {
        synchronized(lock) {
            return assetById[assetId]
        }
    }

    private fun computeHash(data: ByteArray): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun blobPath(root: Path, hash: String): Path =
        root.resolve(BLOBS_DIRECTORY).resolve("$hash.$ASSET_EXTENSION")

    private fun saveIndex(root: Path) {
        val indexPath = root.resolve(INDEX_FILE_NAME)
        val serialized = IndexFile(
            nextAssetId = nextAssetId,
            assets = assetById.values.map {
                AssetRecordJson(
                    assetId = it.assetId,
                    hash = it.hash,
                    width = it.width,
                    height = it.height,
                    createdAt = it.createdAt,
                    byteSize = it.byteSize
                )
            }
        )
        runCatching {
            Files.newBufferedWriter(indexPath).use { writer ->
                CobblemonConfig.GSON.toJson(serialized, writer)
            }
            dirty = false
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to save printed photo asset index {}", indexPath, it)
        }
    }

    private fun loadIndex(root: Path) {
        val indexPath = root.resolve(INDEX_FILE_NAME)
        if (!Files.exists(indexPath)) return

        val parsed = runCatching {
            Files.newBufferedReader(indexPath).use { reader ->
                CobblemonConfig.GSON.fromJson(reader, IndexFile::class.java)
            }
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to read printed photo asset index {}", indexPath, it)
        }.getOrNull() ?: return

        nextAssetId = max(1, parsed.nextAssetId ?: 1)

        parsed.assets?.forEach { json ->
            val record = AssetRecord(
                assetId = json.assetId,
                hash = json.hash,
                width = json.width,
                height = json.height,
                createdAt = json.createdAt,
                byteSize = json.byteSize
            )
            assetById[record.assetId] = record
            idByHash[record.hash] = record.assetId
            if (record.assetId >= nextAssetId) nextAssetId = record.assetId + 1
        }
    }

    // Nullable fields because Gson reflection-based deserialization bypasses Kotlin defaults.
    private data class IndexFile(
        @SerializedName("next_asset_id") val nextAssetId: Int? = null,
        @SerializedName("assets") val assets: List<AssetRecordJson>? = null
    )

    private data class AssetRecordJson(
        @SerializedName("asset_id") val assetId: Int,
        val hash: String,
        val width: Int,
        val height: Int,
        @SerializedName("created_at") val createdAt: Long,
        @SerializedName("byte_size") val byteSize: Int
    )
}
