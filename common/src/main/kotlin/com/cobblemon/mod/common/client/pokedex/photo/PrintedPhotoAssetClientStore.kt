/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.net.messages.client.ui.PrintedPhotoAssetBatchPacket
import com.cobblemon.mod.common.net.messages.client.ui.RequestPrintedPhotoAssetBatchPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Client-side cache of printed-photo asset textures, indexed by asset id.
 *
 * Bounded LRU; evicted textures are closed to free GPU memory.
 * Issuing the same id while a request is pending or while the texture is cached is a no-op.
 */
class PrintedPhotoAssetClientStore {
    private data class CachedTexture(val location: ResourceLocation, val texture: DynamicTexture)

    private val maxEntries = 64
    private val textures = object : LinkedHashMap<Int, CachedTexture>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Int, CachedTexture>?): Boolean {
            if (size <= maxEntries) return false
            eldest?.value?.texture?.close()
            return true
        }
    }
    private val pending = mutableSetOf<Int>()

    fun clear() {
        textures.values.forEach { it.texture.close() }
        textures.clear()
        pending.clear()
    }

    fun getTexture(assetId: Int): ResourceLocation? = textures[assetId]?.location

    fun requestAssets(assetIds: Collection<Int>) {
        val toRequest = assetIds.filter { it > 0 && it !in pending && it !in textures.keys }.distinct()
        if (toRequest.isEmpty()) return
        pending.addAll(toRequest)
        RequestPrintedPhotoAssetBatchPacket(toRequest).sendToServer()
    }

    fun applyAssetBatch(entries: List<PrintedPhotoAssetBatchPacket.Entry>) {
        entries.forEach { entry ->
            pending.remove(entry.assetId)
            decodeAndRegister(entry.assetId, entry.imageData)
        }
    }

    private fun decodeAndRegister(assetId: Int, imageData: ByteArray) {
        textures.remove(assetId)?.texture?.close()

        runCatching {
            // Convert JPEG to PNG in memory because NativeImage only reads PNG.
            val pngBytes = ByteArrayInputStream(imageData).use { input ->
                val bufferedImage = ImageIO.read(input) ?: throw java.io.IOException("Unable to decode printed photo asset")
                ByteArrayOutputStream().also { out -> ImageIO.write(bufferedImage, "PNG", out) }.toByteArray()
            }
            ByteArrayInputStream(pngBytes).use { stream ->
                val image = NativeImage.read(stream)
                val texture = DynamicTexture(image)
                val resource = cobblemonResource("printed_photo/asset_$assetId")
                Minecraft.getInstance().textureManager.register(resource, texture)
                textures[assetId] = CachedTexture(resource, texture)
            }
        }.onFailure {
            Cobblemon.LOGGER.warn("Failed to decode printed photo asset {}", assetId, it)
        }
    }
}
