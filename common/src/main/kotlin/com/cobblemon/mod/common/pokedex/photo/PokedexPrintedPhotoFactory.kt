/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object PokedexPrintedPhotoFactory {
    private const val MAP_SIZE = 128
    private const val MAP_RENDER_VERSION = 2
    private val mapPalette: IntArray = IntArray(256) { packedId -> MapColor.getColorFromPackedId(packedId) }

    fun createPrintedStack(level: ServerLevel, metadata: PokedexPhotoMetadata, imageData: ByteArray): ItemStack? {
        // Reuse an existing map if this blob has already been rendered once.
        val cachedMapId = PokedexPhotoRepository.getMapIdForBlob(metadata.owner, metadata.id, MAP_RENDER_VERSION)
        if (cachedMapId != null) {
            return buildStack(metadata, MapId(cachedMapId))
        }

        val image = decodeImage(imageData) ?: return null
        val resized = resizeToMap(image)

        val mapId = level.freeMapId
        val freshData = MapItemSavedData.createFresh(0.0, 0.0, 0.toByte(), false, true, level.dimension())
        for (y in 0 until MAP_SIZE) {
            for (x in 0 until MAP_SIZE) {
                freshData.setColor(x, y, closestPackedColor(resized.getRGB(x, y)))
            }
        }
        // Lock the map so MapItem.update() won't overwrite our photo pixels with terrain when a player carries it.
        val lockedData = freshData.locked()
        level.setMapData(mapId, lockedData)

        PokedexPhotoRepository.setMapIdForBlob(metadata.owner, metadata.id, mapId.id(), MAP_RENDER_VERSION)
        return buildStack(metadata, mapId)
    }

    private fun buildStack(metadata: PokedexPhotoMetadata, mapId: MapId): ItemStack {
        val stack = ItemStack(CobblemonItems.PRINTED_PHOTO)
        stack.set(DataComponents.MAP_ID, mapId)
        PrintedPhotoComponent.set(
            stack,
            PrintedPhotoComponent(
                photoId = metadata.id,
                owner = metadata.owner,
                index = metadata.index,
                mapId = mapId.id(),
                createdAt = metadata.createdAt,
                detectedSpecies = metadata.detectedSpecies,
                biome = metadata.biome,
                dimension = metadata.dimension,
                timeOfDayTicks = metadata.timeOfDayTicks
            )
        )
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.cobblemon.printed_photo.named", metadata.index))
        return stack
    }

    private fun decodeImage(imageBytes: ByteArray): BufferedImage? {
        return runCatching {
            ByteArrayInputStream(imageBytes).use(ImageIO::read)
        }.getOrNull()
    }

    private fun resizeToMap(source: BufferedImage): BufferedImage {
        val side = minOf(source.width, source.height)
        val startX = (source.width - side) / 2
        val startY = (source.height - side) / 2

        val resized = BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(source, 0, 0, MAP_SIZE, MAP_SIZE, startX, startY, startX + side, startY + side, null)
        graphics.dispose()
        return resized
    }

    private fun closestPackedColor(argb: Int): Byte {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha < 40) return 0

        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        var bestPacked = 0
        var bestDistance = Int.MAX_VALUE
        for (packed in mapPalette.indices) {
            val rgb = mapPalette[packed]
            // MapColor#getColorFromPackedId returns colors in ABGR packing.
            val paletteRed = rgb and 0xFF
            val paletteGreen = (rgb ushr 8) and 0xFF
            val paletteBlue = (rgb ushr 16) and 0xFF
            val dr = red - paletteRed
            val dg = green - paletteGreen
            val db = blue - paletteBlue
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestPacked = packed
                if (distance == 0) break
            }
        }
        return bestPacked.toByte()
    }
}
