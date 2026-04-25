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
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object PokedexPrintedPhotoFactory {
    @Suppress("UNUSED_PARAMETER")
    fun createPrintedStack(level: ServerLevel, metadata: PokedexPhotoMetadata, imageData: ByteArray, printSize: Int): ItemStack? {
        val decoded = decodeImageDimensions(imageData) ?: return null
        val asset = PrintedPhotoAssetRepository.createOrGetAssetFromJpeg(
            jpegBytes = imageData,
            width = decoded.first,
            height = decoded.second
        ) ?: return null
        return buildStack(metadata, asset.assetId, printSize.coerceIn(1, 3))
    }

    private fun buildStack(metadata: PokedexPhotoMetadata, assetId: Int, printSize: Int): ItemStack {
        val stack = ItemStack(CobblemonItems.PRINTED_PHOTO)
        PrintedPhotoComponent.set(
            stack,
            PrintedPhotoComponent(
                photoId = metadata.id,
                owner = metadata.owner,
                index = metadata.index,
                assetId = assetId,
                printSize = printSize,
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

    private fun decodeImageDimensions(imageBytes: ByteArray): Pair<Int, Int>? {
        return runCatching {
            ByteArrayInputStream(imageBytes).use { stream ->
                val image = ImageIO.read(stream) ?: return@runCatching null
                image.width to image.height
            }
        }.getOrNull()
    }
}
