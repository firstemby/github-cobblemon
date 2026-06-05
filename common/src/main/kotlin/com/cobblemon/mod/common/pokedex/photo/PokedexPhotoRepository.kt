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
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min

object PokedexPhotoRepository {
    private const val INDEX_FILE_NAME = "index.json"
    private const val BLOBS_DIRECTORY = "blobs"
    private const val LEGACY_PLAYERS_DIRECTORY = "players"
    private const val PHOTO_EXTENSION = "jpg"
    private const val DEFAULT_FIRST_INDEX = 1L
    private const val ID_INDEX_PAD = 10
    private const val BLOB_CACHE_MAX_ENTRIES = 64

    private val lock = Any()

    private var rootPath: Path? = null
    private var dirty = false
    private val nextIndexByOwner = mutableMapOf<UUID, Long>()
    private val photosByOwner = mutableMapOf<UUID, MutableList<PokedexPhotoMetadata>>()
    private val photosById = mutableMapOf<String, PokedexPhotoMetadata>()
    private val photoToBlobHash = mutableMapOf<String, String>()
    private val blobsByHash = mutableMapOf<String, BlobRecord>()

    // Small bounded LRU. Deduplication means one cached entry serves every photo that shares its bytes.
    private val blobCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>?): Boolean = size > BLOB_CACHE_MAX_ENTRIES
    }

    data class SaveResult(val success: Boolean, val photo: PokedexPhotoMetadata? = null, val errorMessage: String? = null)

    private data class BlobRecord(
        val hash: String,
        var refCount: Int,
        var mapId: Int? = null,
        var mapRenderVersion: Int = 0
    )

    fun initialize(server: MinecraftServer) {
        synchronized(lock) {
            val root = server.getWorldPath(LevelResource.ROOT).resolve("pokedex_photos")
            rootPath = root
            Files.createDirectories(root)
            Files.createDirectories(root.resolve(BLOBS_DIRECTORY))

            nextIndexByOwner.clear()
            photosByOwner.clear()
            photosById.clear()
            photoToBlobHash.clear()
            blobsByHash.clear()
            blobCache.clear()
            dirty = false

            loadIndex(root)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            if (dirty) rootPath?.let(::saveIndex)
            rootPath = null
            nextIndexByOwner.clear()
            photosByOwner.clear()
            photosById.clear()
            photoToBlobHash.clear()
            blobsByHash.clear()
            blobCache.clear()
            dirty = false
        }
    }

    fun saveFromPng(
        owner: UUID,
        pngData: ByteArray,
        detectedSpecies: List<String> = emptyList(),
        biome: String = "minecraft:unknown",
        dimension: String = "minecraft:overworld",
        timeOfDayTicks: Long = 0L,
        raining: Boolean = false,
        thundering: Boolean = false,
        qualityScore: Int = 0,
        qualityBaseScore: Int = qualityScore,
        qualitySpeciesMultiplier: Float = 1F,
        qualitySubjects: List<PokedexPhotoSubjectQuality> = emptyList()
    ): SaveResult {
        synchronized(lock) {
            val root = rootPath ?: return SaveResult(success = false, errorMessage = "Photo storage is unavailable.")

            val currentPhotos = photosByOwner.getOrPut(owner) { mutableListOf() }
            val maxPhotos = max(1, Cobblemon.config.pokedexScannerPhotoLimitPerPlayer)
            if (currentPhotos.size >= maxPhotos) {
                return SaveResult(success = false, errorMessage = "Photo limit reached ($maxPhotos).")
            }

            val decoded = decodeImage(pngData) ?: return SaveResult(success = false, errorMessage = "Invalid photo data.")
            val outputSize = max(64, Cobblemon.config.pokedexScannerPhotoResolution)
            val resized = resizeToSquare(decoded, outputSize)
            val quality = Cobblemon.config.pokedexScannerPhotoJpegQuality.toFloat().coerceIn(0.1F, 1.0F)
            val encoded = encodeJpeg(resized, quality) ?: return SaveResult(success = false, errorMessage = "Failed to encode photo.")

            val blobHash = computeHash(encoded)

            if (!blobsByHash.containsKey(blobHash)) {
                val blobFile = blobPath(root, blobHash)
                val saved = runCatching {
                    Files.createDirectories(blobFile.parent)
                    Files.write(blobFile, encoded)
                }.onFailure {
                    Cobblemon.LOGGER.warn("Failed to save pokedex photo blob {}", blobHash, it)
                }.isSuccess
                if (!saved) return SaveResult(success = false, errorMessage = "Server failed to write photo file.")
                blobsByHash[blobHash] = BlobRecord(hash = blobHash, refCount = 0)
            }
            blobsByHash[blobHash]!!.refCount++

            var nextIndex = nextIndexByOwner.getOrDefault(owner, DEFAULT_FIRST_INDEX)
            var id = createPhotoId(owner, nextIndex)
            while (photosById.containsKey(id)) {
                nextIndex += 1
                id = createPhotoId(owner, nextIndex)
            }

            val metadata = PokedexPhotoMetadata(
                id = id,
                owner = owner,
                index = nextIndex,
                width = outputSize,
                height = outputSize,
                createdAt = System.currentTimeMillis(),
                dataSize = encoded.size,
                detectedSpecies = detectedSpecies.distinct(),
                biome = biome,
                dimension = dimension,
                timeOfDayTicks = timeOfDayTicks.coerceIn(0L, 23999L),
                raining = raining,
                thundering = thundering,
                qualityScore = qualityScore.coerceIn(0, 5000),
                qualityBaseScore = qualityBaseScore.coerceIn(0, 5000),
                qualitySpeciesMultiplier = qualitySpeciesMultiplier.coerceAtLeast(0F),
                qualitySubjects = qualitySubjects
            )

            nextIndexByOwner[owner] = nextIndex + 1
            currentPhotos.add(0, metadata)
            photosById[id] = metadata
            photoToBlobHash[id] = blobHash
            blobCache[blobHash] = encoded
            dirty = true
            saveIndex(root)

            return SaveResult(success = true, photo = metadata)
        }
    }

    fun listFor(owner: UUID): List<PokedexPhotoMetadata> {
        synchronized(lock) {
            return photosByOwner[owner]?.toList() ?: emptyList()
        }
    }

    fun getPhoto(owner: UUID, id: String): ByteArray? {
        synchronized(lock) {
            val root = rootPath ?: return null
            val metadata = photosById[id] ?: return null
            if (metadata.owner != owner) return null
            val hash = photoToBlobHash[id] ?: return null
            blobCache[hash]?.let { return it }
            val path = blobPath(root, hash)
            if (!Files.exists(path)) return null
            val bytes = runCatching { Files.readAllBytes(path) }
                .onFailure { Cobblemon.LOGGER.warn("Failed to read pokedex photo blob {}", hash, it) }
                .getOrNull() ?: return null
            blobCache[hash] = bytes
            return bytes
        }
    }

    fun getMetadata(owner: UUID, id: String): PokedexPhotoMetadata? {
        synchronized(lock) {
            val metadata = photosById[id] ?: return null
            return metadata.takeIf { it.owner == owner }
        }
    }

    fun delete(owner: UUID, id: String): Boolean {
        synchronized(lock) {
            val root = rootPath ?: return false
            val metadata = photosById[id] ?: return false
            if (metadata.owner != owner) return false

            val removedFromOwner = photosByOwner[owner]?.removeIf { it.id == id } == true
            photosById.remove(id)

            val hash = photoToBlobHash.remove(id)
            if (hash != null) {
                val blob = blobsByHash[hash]
                if (blob != null) {
                    blob.refCount--
                    if (blob.refCount <= 0) {
                        blobsByHash.remove(hash)
                        blobCache.remove(hash)
                        runCatching { Files.deleteIfExists(blobPath(root, hash)) }
                            .onFailure { Cobblemon.LOGGER.warn("Failed to delete pokedex photo blob {}", hash, it) }
                    }
                }
            }

            if (removedFromOwner) {
                dirty = true
                saveIndex(root)
            }
            return removedFromOwner
        }
    }

    fun setMapIdForBlob(owner: UUID, photoId: String, mapId: Int, renderVersion: Int) {
        // Cached optimization only — persist on the next real mutation or on shutdown, not eagerly.
        synchronized(lock) {
            val hash = photoToBlobHash[photoId] ?: return
            val blob = blobsByHash[hash] ?: return
            if (blob.mapId == mapId && blob.mapRenderVersion == renderVersion) return
            blob.mapId = mapId
            blob.mapRenderVersion = renderVersion
            dirty = true
        }
    }

    private fun computeHash(data: ByteArray): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun blobPath(root: Path, hash: String): Path =
        root.resolve(BLOBS_DIRECTORY).resolve("$hash.$PHOTO_EXTENSION")

    private fun legacyPhotoPath(root: Path, metadata: PokedexPhotoMetadata): Path =
        root.resolve(LEGACY_PLAYERS_DIRECTORY).resolve(metadata.owner.toString()).resolve("${metadata.id}.$PHOTO_EXTENSION")

    private fun decodeImage(imageBytes: ByteArray): BufferedImage? {
        return runCatching {
            ByteArrayInputStream(imageBytes).use(ImageIO::read)
        }.getOrNull()
    }

    private fun resizeToSquare(image: BufferedImage, size: Int): BufferedImage {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val side = min(sourceWidth, sourceHeight)
        val startX = (sourceWidth - side) / 2
        val startY = (sourceHeight - side) / 2

        val target = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(image, 0, 0, size, size, startX, startY, startX + side, startY + side, null)
        graphics.dispose()
        return target
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray? {
        val writer = ImageIO.getImageWritersByFormatName(PHOTO_EXTENSION).asSequence().firstOrNull() ?: return null
        val output = ByteArrayOutputStream()
        val imageOutput = MemoryCacheImageOutputStream(output)
        val params = writer.defaultWriteParam
        if (params.canWriteCompressed()) {
            params.compressionMode = ImageWriteParam.MODE_EXPLICIT
            params.compressionQuality = quality
        }

        return runCatching {
            writer.output = imageOutput
            writer.write(null, IIOImage(image, null, null), params)
            imageOutput.flush()
            output.toByteArray()
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to encode pokedex photo jpeg.", it)
        }.getOrNull().also {
            writer.dispose()
            imageOutput.close()
            output.close()
        }
    }

    private fun createPhotoId(owner: UUID, index: Long): String {
        val ownerKey = owner.toString().replace("-", "")
        val indexKey = index.toString().padStart(ID_INDEX_PAD, '0')
        return "${ownerKey}_$indexKey"
    }

    private fun saveIndex(root: Path) {
        val indexPath = root.resolve(INDEX_FILE_NAME)
        val serialized = IndexFile(
            nextIndices = nextIndexByOwner.mapKeys { it.key.toString() },
            photos = photosById.values.map {
                PhotoRecord(
                    id = it.id,
                    owner = it.owner.toString(),
                    index = it.index,
                    width = it.width,
                    height = it.height,
                    createdAt = it.createdAt,
                    dataSize = it.dataSize,
                    detectedSpecies = it.detectedSpecies.orEmpty(),
                    biome = it.biome,
                    dimension = it.dimension,
                    timeOfDayTicks = it.timeOfDayTicks,
                    raining = it.raining,
                    thundering = it.thundering,
                    qualityScore = it.qualityScore,
                    qualityBaseScore = it.qualityBaseScore,
                    qualitySpeciesMultiplier = it.qualitySpeciesMultiplier,
                    qualitySubjects = sanitizeQualitySubjects(it.qualitySubjects),
                    blobHash = photoToBlobHash[it.id] ?: ""
                )
            },
            blobs = blobsByHash.values.map {
                BlobIndexRecord(
                    hash = it.hash,
                    refCount = it.refCount,
                    mapId = it.mapId,
                    mapRenderVersion = it.mapRenderVersion
                )
            }
        )

        runCatching {
            Files.newBufferedWriter(indexPath).use { writer ->
                CobblemonConfig.GSON.toJson(serialized, writer)
            }
            dirty = false
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to save pokedex photo index {}", indexPath, it)
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
            Cobblemon.LOGGER.warn("Failed to read pokedex photo index {}", indexPath, it)
        }.getOrNull() ?: return

        val maxIndexByOwner = mutableMapOf<UUID, Long>()

        parsed.nextIndices?.forEach { (owner, nextValue) ->
            runCatching { UUID.fromString(owner) }.getOrNull()?.let { uuid ->
                nextIndexByOwner[uuid] = max(DEFAULT_FIRST_INDEX, nextValue)
            }
        }

        parsed.blobs?.forEach { record ->
            blobsByHash[record.hash] = BlobRecord(
                hash = record.hash,
                refCount = record.refCount,
                mapId = record.mapId,
                mapRenderVersion = record.mapRenderVersion ?: 0
            )
        }

        var needsSave = false

        parsed.photos?.forEach { record ->
            val ownerUuid = runCatching { UUID.fromString(record.owner) }.getOrNull() ?: return@forEach

            val blobHash = if (!record.blobHash.isNullOrEmpty()) {
                record.blobHash
            } else {
                // Legacy migration: move old per-player file into blobs/
                val metadata = PokedexPhotoMetadata(
                    id = record.id, owner = ownerUuid, index = record.index,
                    width = record.width, height = record.height,
                    createdAt = record.createdAt, dataSize = record.dataSize
                )
                val legacyPath = legacyPhotoPath(root, metadata)
                if (!Files.exists(legacyPath)) return@forEach
                val bytes = runCatching { Files.readAllBytes(legacyPath) }.getOrNull() ?: return@forEach
                val hash = computeHash(bytes)
                if (!blobsByHash.containsKey(hash)) {
                    val dest = blobPath(root, hash)
                    val copied = runCatching {
                        Files.createDirectories(dest.parent)
                        Files.copy(legacyPath, dest)
                    }.onFailure {
                        Cobblemon.LOGGER.warn("Failed to migrate legacy photo {} to blob storage", record.id, it)
                    }.isSuccess
                    if (!copied) return@forEach
                    blobsByHash[hash] = BlobRecord(hash = hash, refCount = 0)
                }
                runCatching { Files.deleteIfExists(legacyPath) }
                needsSave = true
                hash
            }

            if (!Files.exists(blobPath(root, blobHash))) return@forEach

            val metadata = PokedexPhotoMetadata(
                id = record.id, owner = ownerUuid, index = record.index,
                width = record.width, height = record.height,
                createdAt = record.createdAt, dataSize = record.dataSize,
                detectedSpecies = record.detectedSpecies ?: emptyList(),
                biome = record.biome ?: "minecraft:unknown",
                dimension = record.dimension ?: "minecraft:overworld",
                timeOfDayTicks = record.timeOfDayTicks ?: 0L,
                raining = record.raining ?: false,
                thundering = record.thundering ?: false,
                qualityScore = record.qualityScore,
                qualityBaseScore = record.qualityBaseScore,
                qualitySpeciesMultiplier = record.qualitySpeciesMultiplier,
                qualitySubjects = sanitizeQualitySubjects(record.qualitySubjects)
            )
            photosById[metadata.id] = metadata
            photosByOwner.getOrPut(ownerUuid) { mutableListOf() }.add(metadata)
            photoToBlobHash[metadata.id] = blobHash
            maxIndexByOwner[ownerUuid] = max(maxIndexByOwner.getOrDefault(ownerUuid, 0L), metadata.index)
        }

        // Recompute ref counts from actual photo->blob mapping to stay consistent after any migration.
        if (needsSave) {
            blobsByHash.values.forEach { it.refCount = 0 }
            photoToBlobHash.values.forEach { hash -> blobsByHash[hash]?.refCount = (blobsByHash[hash]?.refCount ?: 0) + 1 }
        }

        photosByOwner.values.forEach { entries -> entries.sortByDescending { it.createdAt } }

        maxIndexByOwner.forEach { (owner, maxIndex) ->
            val fromIndex = maxIndex + 1
            val existing = nextIndexByOwner.getOrDefault(owner, DEFAULT_FIRST_INDEX)
            nextIndexByOwner[owner] = max(existing, fromIndex)
        }

        if (needsSave) {
            saveIndex(root)
        }
    }

    private fun sanitizeQualitySubjects(subjects: List<PokedexPhotoSubjectQuality>?): List<PokedexPhotoSubjectQuality> {
        return subjects.orEmpty().map { subject ->
            subject.copy(activeAnimations = subject.activeAnimations.orEmpty())
        }
    }

    // Fields are nullable because Gson's reflection-based deserialization bypasses Kotlin default values
    // — an older index.json written without blob storage won't have the "blobs" key at all.
    private data class IndexFile(
        @SerializedName("next_indices") val nextIndices: Map<String, Long>? = null,
        @SerializedName("photos") val photos: List<PhotoRecord>? = null,
        @SerializedName("blobs") val blobs: List<BlobIndexRecord>? = null
    )

    private data class PhotoRecord(
        val id: String,
        val owner: String,
        val index: Long,
        val width: Int,
        val height: Int,
        @SerializedName("created_at") val createdAt: Long,
        @SerializedName("data_size") val dataSize: Int,
        @SerializedName("detected_species") val detectedSpecies: List<String>? = null,
        @SerializedName("biome") val biome: String? = null,
        @SerializedName("dimension") val dimension: String? = null,
        @SerializedName("time_of_day_ticks") val timeOfDayTicks: Long? = null,
        @SerializedName("raining") val raining: Boolean? = null,
        @SerializedName("thundering") val thundering: Boolean? = null,
        @SerializedName("quality_score") val qualityScore: Int,
        @SerializedName("quality_base_score") val qualityBaseScore: Int,
        @SerializedName("quality_species_multiplier") val qualitySpeciesMultiplier: Float,
        @SerializedName("quality_subjects") val qualitySubjects: List<PokedexPhotoSubjectQuality>,
        // Nullable because Gson's reflection-based deserialization bypasses Kotlin default values;
        // older indexes written before blob storage simply lack this field.
        @SerializedName("blob_hash") val blobHash: String? = null
    )

    private data class BlobIndexRecord(
        val hash: String,
        @SerializedName("ref_count") val refCount: Int,
        @SerializedName("map_id") val mapId: Int? = null,
        @SerializedName("map_render_version") val mapRenderVersion: Int? = null
    )
}
