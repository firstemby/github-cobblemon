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
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_WIDTH
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.client.ui.DeletePokedexPhotoPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

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

    override fun onSelected(gui: PokedexGUI) {
        CobblemonClient.pokedexPhotoStore.requestGallery()
    }

    override fun onClose(gui: PokedexGUI) = Unit

    override fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val photoStore = CobblemonClient.pokedexPhotoStore
        val photos = photoStore.getPhotos()

        val countText = lang("ui.pokedex.gallery.count", photos.size).bold()
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = countText,
            x = x + 173,
            y = y + 31,
            shadow = true,
            centered = true
        )

        if (photos.isEmpty()) {
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
        photos.take(MAX_THUMBS).forEachIndexed { index, photo ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = startX + (col * (THUMB_WIDTH + THUMB_GAP))
            val thumbY = startY + (row * (THUMB_HEIGHT + THUMB_GAP))

            context.fill(thumbX - 1, thumbY - 1, thumbX + THUMB_WIDTH + 1, thumbY + THUMB_HEIGHT + 1, -14536682)

            val texture = photoStore.getTexture(photo.id)
            if (texture != null) {
                blitk(
                    matrixStack = context.pose(),
                    texture = texture,
                    x = thumbX,
                    y = thumbY,
                    width = THUMB_WIDTH,
                    height = THUMB_HEIGHT
                )
            } else {
                context.fill(thumbX, thumbY, thumbX + THUMB_WIDTH, thumbY + THUMB_HEIGHT, -13027015)
            }
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokedex.gallery.delete_hint"),
            x = x + 173,
            y = y + 157,
            shadow = true,
            centered = true
        )
    }

    override fun mouseClicked(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 1) return false

        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2
        val photo = photoAt(mouseX, mouseY, x, y, CobblemonClient.pokedexPhotoStore.getPhotos().take(MAX_THUMBS)) ?: return false
        DeletePokedexPhotoPacket(photo.id).sendToServer()
        return true
    }

    private fun photoAt(
        mouseX: Double,
        mouseY: Double,
        x: Int,
        y: Int,
        visiblePhotos: List<PokedexPhotoMetadata>
    ): PokedexPhotoMetadata? {
        val startX = x + 28
        val startY = y + 43

        visiblePhotos.forEachIndexed { index, photo ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = startX + (col * (THUMB_WIDTH + THUMB_GAP))
            val thumbY = startY + (row * (THUMB_HEIGHT + THUMB_GAP))

            if (mouseX >= thumbX && mouseX < thumbX + THUMB_WIDTH && mouseY >= thumbY && mouseY < thumbY + THUMB_HEIGHT) {
                return photo
            }
        }

        return null
    }
}
