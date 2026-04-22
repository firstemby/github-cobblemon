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
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min

object PokedexPhotoRepository {
    private const val INDEX_FILE_NAME = "index.json"
    private const val PLAYERS_DIRECTORY = "players"
    private const val PHOTO_EXTENSION = "jpg"
    private const val DEFAULT_FIRST_INDEX = 1L
    private const val ID_INDEX_PAD = 10

    private val lock = Any()

    private var rootPath: Path? = null
    private val nextIndexByOwner = mutableMapOf<UUID, Long>()
    private val photosByOwner = mutableMapOf<UUID, MutableList<PokedexPhotoMetadata>>()
    private val photosById = mutableMapOf<String, PokedexPhotoMetadata>()

    data class SaveResult(val success: Boolean, val photo: PokedexPhotoMetadata? = null, val errorMessage: String? = null)

    fun initialize(server: MinecraftServer) {
        synchronized(lock) {
            val root = server.getWorldPath(LevelResource.ROOT).resolve("pokedex_photos")
            rootPath = root
            Files.createDirectories(root)
            Files.createDirectories(root.resolve(PLAYERS_DIRECTORY))

            nextIndexByOwner.clear()
            photosByOwner.clear()
            photosById.clear()

            loadIndex(root)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            rootPath?.let(::saveIndex)
            rootPath = null
            nextIndexByOwner.clear()
            photosByOwner.clear()
            photosById.clear()
        }
    }

    fun saveFromPng(owner: UUID, pngData: ByteArray): SaveResult {
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
                dataSize = encoded.size
            )

            val file = photoPath(root, metadata)
            val saved = runCatching {
                Files.createDirectories(file.parent)
                Files.write(file, encoded)
            }.onFailure {
                Cobblemon.LOGGER.warn("Failed to save pokedex photo {}", id, it)
            }.isSuccess

            if (!saved) {
                return SaveResult(success = false, errorMessage = "Server failed to write photo file.")
            }

            nextIndexByOwner[owner] = nextIndex + 1
            currentPhotos.add(0, metadata)
            photosById[id] = metadata
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
            val path = photoPath(root, metadata)
            if (!Files.exists(path)) return null
            return runCatching { Files.readAllBytes(path) }
                .onFailure { Cobblemon.LOGGER.warn("Failed to read pokedex photo {}", id, it) }
                .getOrNull()
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
            runCatching { Files.deleteIfExists(photoPath(root, metadata)) }
                .onFailure { Cobblemon.LOGGER.warn("Failed to delete pokedex photo {}", id, it) }

            if (removedFromOwner) {
                saveIndex(root)
            }
            return removedFromOwner
        }
    }

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

    private fun photoPath(root: Path, metadata: PokedexPhotoMetadata): Path {
        return root
            .resolve(PLAYERS_DIRECTORY)
            .resolve(metadata.owner.toString())
            .resolve("${metadata.id}.$PHOTO_EXTENSION")
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
                    dataSize = it.dataSize
                )
            }
        )

        runCatching {
            Files.newBufferedWriter(indexPath).use { writer ->
                CobblemonConfig.GSON.toJson(serialized, writer)
            }
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

        parsed.nextIndices.forEach { (owner, nextValue) ->
            runCatching {
                val ownerUuid = UUID.fromString(owner)
                nextIndexByOwner[ownerUuid] = max(DEFAULT_FIRST_INDEX, nextValue)
            }
        }

        parsed.photos.forEach { record ->
            val ownerUuid = runCatching { UUID.fromString(record.owner) }.getOrNull() ?: return@forEach
            val metadata = PokedexPhotoMetadata(
                id = record.id,
                owner = ownerUuid,
                index = record.index,
                width = record.width,
                height = record.height,
                createdAt = record.createdAt,
                dataSize = record.dataSize
            )

            val path = photoPath(root, metadata)
            if (!Files.exists(path)) return@forEach

            photosById[metadata.id] = metadata
            photosByOwner.getOrPut(ownerUuid) { mutableListOf() }.add(metadata)
            maxIndexByOwner[ownerUuid] = max(maxIndexByOwner.getOrDefault(ownerUuid, 0L), metadata.index)
        }

        photosByOwner.values.forEach { entries ->
            entries.sortByDescending { it.createdAt }
        }

        maxIndexByOwner.forEach { (owner, maxIndex) ->
            val fromIndex = maxIndex + 1
            val existing = nextIndexByOwner.getOrDefault(owner, DEFAULT_FIRST_INDEX)
            nextIndexByOwner[owner] = max(existing, fromIndex)
        }
    }

    private data class IndexFile(
        @SerializedName("next_indices")
        val nextIndices: Map<String, Long> = emptyMap(),
        @SerializedName("photos")
        val photos: List<PhotoRecord> = emptyList()
    )

    private data class PhotoRecord(
        val id: String,
        val owner: String,
        val index: Long,
        val width: Int,
        val height: Int,
        @SerializedName("created_at")
        val createdAt: Long,
        @SerializedName("data_size")
        val dataSize: Int
    )
}
