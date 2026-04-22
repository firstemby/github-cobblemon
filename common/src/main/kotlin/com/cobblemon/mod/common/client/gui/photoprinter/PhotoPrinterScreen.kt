/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.photoprinter

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.block.photoprinter.PhotoPrinterMenu
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.ui.CraftPrintedPhotoPacket
import com.cobblemon.mod.common.net.messages.client.ui.SetPhotoPrinterSelectionPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class PhotoPrinterScreen(menu: PhotoPrinterMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<PhotoPrinterMenu>(menu, inventory, title) {
    companion object {
        private const val BACKGROUND_WIDTH = 206
        private const val BACKGROUND_HEIGHT = 222

        private const val GRID_COLUMNS = 4
        private const val GRID_ROWS = 3
        private const val THUMB_SIZE = 44
        private const val THUMB_GAP = 4
        private const val GRID_LEFT = 8
        private const val GRID_TOP = 18
        private const val MAX_THUMBS = GRID_COLUMNS * GRID_ROWS
    }

    private var selectedPhotoId: String? = null
    private var printButton: Button? = null

    override fun init() {
        imageWidth = BACKGROUND_WIDTH
        imageHeight = BACKGROUND_HEIGHT
        super.init()

        printButton = addRenderableWidget(
            Button.builder(Component.translatable("cobblemon.ui.photo_printer.print")) {
                CraftPrintedPhotoPacket(menu.blockPos(), selectedPhotoId).sendToServer()
            }.pos(leftPos + 127, topPos + 59).size(66, 20).build()
        )
        CobblemonClient.pokedexPhotoStore.requestGallery()
    }

    override fun containerTick() {
        super.containerTick()
        if (selectedPhotoId != null && CobblemonClient.pokedexPhotoStore.getPhotos().none { it.id == selectedPhotoId }) {
            selectedPhotoId = null
        }
        printButton?.active = selectedPhotoId != null
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x0 = leftPos
        val y0 = topPos
        guiGraphics.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, -14671840)
        guiGraphics.fill(x0 + 1, y0 + 1, x0 + imageWidth - 1, y0 + imageHeight - 1, -13027015)

        val gridStartX = leftPos + GRID_LEFT
        val gridStartY = topPos + GRID_TOP
        val photos = CobblemonClient.pokedexPhotoStore.getPhotos().take(MAX_THUMBS)
        photos.forEachIndexed { index, photo ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = gridStartX + col * (THUMB_SIZE + THUMB_GAP)
            val thumbY = gridStartY + row * (THUMB_SIZE + THUMB_GAP)

            val selected = photo.id == selectedPhotoId
            val borderColor = if (selected) -65536 else -14079703
            guiGraphics.fill(thumbX - 1, thumbY - 1, thumbX + THUMB_SIZE + 1, thumbY + THUMB_SIZE + 1, borderColor)

            val texture = CobblemonClient.pokedexPhotoStore.getTexture(photo.id)
            if (texture != null) {
                blitk(guiGraphics.pose(), texture, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE)
            } else {
                guiGraphics.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, -12040120)
            }
        }
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, title, 8, 6, 4210752, false)
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 4210752, false)

        val total = CobblemonClient.pokedexPhotoStore.getPhotos().size
        val maxAllowed = CobblemonClient.pokedexPhotoStore.maxPhotosAllowed
        guiGraphics.drawString(font, Component.translatable("cobblemon.ui.photo_printer.count", total, maxAllowed), 127, 20, 4210752, false)

        val selected = selectedPhotoId
        if (selected != null) {
            guiGraphics.drawString(font, Component.translatable("cobblemon.ui.photo_printer.selected", selected), 127, 34, 4210752, false)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val photos = CobblemonClient.pokedexPhotoStore.getPhotos().take(MAX_THUMBS)
        val selected = photoAt(mouseX, mouseY, photos)
        if (selected != null) {
            selectedPhotoId = selected.id
            SetPhotoPrinterSelectionPacket(menu.blockPos(), selected.id).sendToServer()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun photoAt(mouseX: Double, mouseY: Double, visiblePhotos: List<PokedexPhotoMetadata>): PokedexPhotoMetadata? {
        val gridStartX = leftPos + GRID_LEFT
        val gridStartY = topPos + GRID_TOP

        visiblePhotos.forEachIndexed { index, photo ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = gridStartX + col * (THUMB_SIZE + THUMB_GAP)
            val thumbY = gridStartY + row * (THUMB_SIZE + THUMB_GAP)

            if (mouseX >= thumbX && mouseX < thumbX + THUMB_SIZE && mouseY >= thumbY && mouseY < thumbY + THUMB_SIZE) {
                return photo
            }
        }
        return null
    }
}
