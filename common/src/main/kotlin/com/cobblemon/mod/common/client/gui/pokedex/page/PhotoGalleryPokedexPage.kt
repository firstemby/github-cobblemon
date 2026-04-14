/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.File
import kotlin.math.min

class PhotoGalleryPokedexPage(
    override val id: ResourceLocation,
    override val titleKey: String
) : PokedexGuiPage {
    companion object {
        private const val GRID_COLUMNS = 4
        private const val GRID_ROWS = 3
        private const val THUMB_WIDTH = 68
        private const val THUMB_HEIGHT = 36
        private const val THUMB_GAP = 4
        private const val MAX_THUMBS = GRID_COLUMNS * GRID_ROWS
    }

    private val textures = mutableMapOf<String, Pair<ResourceLocation, DynamicTexture>>()
    private var screenshots: List<File> = emptyList()

    override fun onSelected(gui: PokedexGUI) {
        refreshScreenshots()
    }

    override fun onClose(gui: PokedexGUI) {
        textures.values.forEach { (_, texture) -> texture.close() }
        textures.clear()
    }

    override fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val countText = lang("ui.pokedex.gallery.count", screenshots.size).bold()
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = countText,
            x = x + 173,
            y = y + 31,
            shadow = true,
            centered = true
        )

        if (screenshots.isEmpty()) {
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.pokedex.gallery.empty").bold(),
                x = x + 173,
                y = y + 92,
                shadow = true,
                centered = true
            )
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.pokedex.gallery.empty_hint"),
                x = x + 173,
                y = y + 103,
                shadow = true,
                centered = true
            )
            return
        }

        val startX = x + 28
        val startY = y + 43
        val visible = screenshots.take(MAX_THUMBS)
        visible.forEachIndexed { index, file ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = startX + (col * (THUMB_WIDTH + THUMB_GAP))
            val thumbY = startY + (row * (THUMB_HEIGHT + THUMB_GAP))

            context.fill(thumbX - 1, thumbY - 1, thumbX + THUMB_WIDTH + 1, thumbY + THUMB_HEIGHT + 1, -14536682)

            val texture = getTexture(file) ?: return@forEachIndexed
            blitk(
                matrixStack = context.pose(),
                texture = texture,
                x = thumbX,
                y = thumbY,
                width = THUMB_WIDTH,
                height = THUMB_HEIGHT
            )
        }
    }

    private fun refreshScreenshots() {
        val screenshotDirectory = File(Minecraft.getInstance().gameDirectory, "screenshots/cobblemon")
        screenshots = screenshotDirectory
            .listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending(File::lastModified)
            ?: emptyList()

        val activeKeys = screenshots.map(::textureKey).toSet()
        val stale = textures.keys.filterNot { it in activeKeys }
        stale.forEach { key ->
            textures.remove(key)?.second?.close()
        }
    }

    private fun getTexture(file: File): ResourceLocation? {
        val key = textureKey(file)
        textures[key]?.let { return it.first }

        return runCatching {
            file.inputStream().use { stream ->
                val nativeImage = NativeImage.read(stream)
                val texture = DynamicTexture(nativeImage)
                val location = cobblemonResource("pokedex/gallery/${key.hashCode().toUInt().toString(16)}")
                Minecraft.getInstance().textureManager.register(location, texture)
                textures[key] = location to texture
                location
            }
        }.getOrNull()
    }

    private fun textureKey(file: File): String = "${file.absolutePath}:${file.lastModified()}:${min(file.length(), Int.MAX_VALUE.toLong())}"
}