/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoDataPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPokedexPhotoGalleryPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream

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

    fun applyPhotoData(photoId: String, imageData: ByteArray) {
        pendingPhotos.remove(photoId)
        textures.remove(photoId)?.texture?.close()

        val location = runCatching {
            ByteArrayInputStream(imageData).use { stream ->
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

    fun getTexture(photoId: String): ResourceLocation? {
        val cached = textures[photoId]
        if (cached != null) {
            return cached.location
        }

        requestPhoto(photoId)
        return null
    }

    fun requestGallery() {
        RequestPokedexPhotoGalleryPacket().sendToServer()
    }

    fun requestPhoto(photoId: String) {
        if (photoId in pendingPhotos || textures.containsKey(photoId)) {
            return
        }

        pendingPhotos.add(photoId)
        RequestPokedexPhotoDataPacket(photoId).sendToServer()
    }
}

