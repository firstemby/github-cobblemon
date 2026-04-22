/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoBatchPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoBatchPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoGalleryPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PokedexPhotoClientStore {
    private data class CachedTexture(val location: ResourceLocation, val texture: DynamicTexture)

    private val photos = mutableListOf<PokedexPhotoMetadata>()
    private val textures = mutableMapOf<String, CachedTexture>()
    private val pendingPhotos = mutableSetOf<String>()

    var maxPhotosAllowed: Int = 0
        private set

    fun clear() {
        textures.values.forEach { it.texture.close() }
        textures.clear()
        pendingPhotos.clear()
        photos.clear()
        maxPhotosAllowed = 0
    }

    fun applyGallery(photoList: List<PokedexPhotoMetadata>, maxAllowed: Int) {
        photos.clear()
        photos.addAll(photoList.sortedByDescending { it.createdAt })
        maxPhotosAllowed = maxAllowed

        val activeIds = photos.map { it.id }.toSet()
        val stale = textures.keys.filterNot { it in activeIds }
        stale.forEach { id ->
            textures.remove(id)?.texture?.close()
            pendingPhotos.remove(id)
        }
    }

    fun applyPhotoBatch(entries: List<PokedexPhotoBatchPacket.Entry>) {
        entries.forEach { entry ->
            // Skip entries for photos that were evicted while the response was in flight.
            if (entry.photoId !in pendingPhotos) return@forEach
            decodeAndRegister(entry.photoId, entry.imageData)
        }
    }

    private fun decodeAndRegister(photoId: String, imageData: ByteArray) {
        pendingPhotos.remove(photoId)
        textures.remove(photoId)?.texture?.close()

        val location = runCatching {
            // Server stores photos as JPEG; NativeImage only reads PNG, so convert in memory first.
            val pngBytes = ByteArrayInputStream(imageData).use { input ->
                val bufferedImage = ImageIO.read(input) ?: throw java.io.IOException("Unable to decode image")
                ByteArrayOutputStream().also { out -> ImageIO.write(bufferedImage, "PNG", out) }.toByteArray()
            }
            ByteArrayInputStream(pngBytes).use { stream ->
                val image = NativeImage.read(stream)
                val texture = DynamicTexture(image)
                val resource = cobblemonResource("pokedex/gallery/${photoId.lowercase()}")
                Minecraft.getInstance().textureManager.register(resource, texture)
                textures[photoId] = CachedTexture(resource, texture)
                resource
            }
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to decode photo {}", photoId, it)
        }.getOrNull()

        if (location == null) {
            textures.remove(photoId)?.texture?.close()
        }
    }

    fun getPhotos(): List<PokedexPhotoMetadata> = photos

    fun getTexture(photoId: String): ResourceLocation? = textures[photoId]?.location

    fun requestGallery() {
        RequestPokedexPhotoGalleryPacket().sendToServer()
    }

    /** Request any of [ids] that are not cached or already in flight, in a single batch. */
    fun prefetch(ids: Collection<String>) {
        val toRequest = ids.filter { it !in pendingPhotos && it !in textures.keys }.distinct()
        if (toRequest.isEmpty()) return
        pendingPhotos.addAll(toRequest)
        RequestPokedexPhotoBatchPacket(toRequest).sendToServer()
    }

    /** Close and drop any textures (and pending requests) whose ids are not in [keepIds]. */
    fun retain(keepIds: Set<String>) {
        val toEvict = textures.keys.filterNot { it in keepIds }.toList()
        toEvict.forEach { id -> textures.remove(id)?.texture?.close() }
        val stalePending = pendingPhotos.filterNot { it in keepIds }.toSet()
        pendingPhotos.removeAll(stalePending)
    }
}
