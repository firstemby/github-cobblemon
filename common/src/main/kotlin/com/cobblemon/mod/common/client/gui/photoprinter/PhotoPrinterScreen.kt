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
import com.cobblemon.mod.common.client.gui.pokedex.widgets.SearchWidget
import com.cobblemon.mod.common.client.pokedex.photo.PokedexPhotoSearch
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
        private const val BACKGROUND_WIDTH = 256
        private const val BACKGROUND_HEIGHT = 270

        private const val GRID_COLUMNS = 3
        private const val GRID_ROWS = 3
        private const val BUFFER_ROWS = 1
        private const val THUMB_SIZE = 36
        private const val THUMB_GAP = 4
        private const val GRID_LEFT = 8
        private const val SEARCH_TOP = 8
        private const val SEARCH_WIDTH = GRID_COLUMNS * THUMB_SIZE + (GRID_COLUMNS - 1) * THUMB_GAP
        private const val SEARCH_HEIGHT = 17
        private const val GRID_TOP = SEARCH_TOP + SEARCH_HEIGHT + 8
        private const val GRID_WIDTH = SEARCH_WIDTH
        private const val GRID_HEIGHT = GRID_ROWS * THUMB_SIZE + (GRID_ROWS - 1) * THUMB_GAP

        private const val PREVIEW_LEFT = GRID_LEFT + GRID_WIDTH + 20
        private const val PREVIEW_TOP = GRID_TOP
        private const val PREVIEW_SIZE = 96

        private const val INFO_LEFT = PREVIEW_LEFT
        private const val INFO_TOP = PREVIEW_TOP + PREVIEW_SIZE + 6

        private const val CONTROL_ROW_Y = GRID_TOP + GRID_HEIGHT + 8
        private const val OUTPUT_SLOT_X = PhotoPrinterMenu.OUTPUT_SLOT_X
        private const val OUTPUT_SLOT_Y = PhotoPrinterMenu.OUTPUT_SLOT_Y
        private const val INPUT_SLOT_X = PhotoPrinterMenu.INPUT_SLOT_X
        private const val INPUT_SLOT_Y = PhotoPrinterMenu.INPUT_SLOT_Y
        private const val PRINT_BUTTON_X = 46
        private const val PRINT_BUTTON_Y = CONTROL_ROW_Y - 1
        private const val PRINT_BUTTON_WIDTH = 124
        private const val PRINT_BUTTON_HEIGHT = 20
        private const val MAX_THUMBS = GRID_COLUMNS * GRID_ROWS
    }

    private var selectedPhotoId: String? = null
    private var scrollOffsetRows = 0
    private var searchQuery = ""
    private var filteredPhotoCount = 0
    private var searchWidget: SearchWidget? = null
    private var printButton: Button? = null

    override fun init() {
        imageWidth = BACKGROUND_WIDTH
        imageHeight = BACKGROUND_HEIGHT
        super.init()
        scrollOffsetRows = 0

        searchWidget = SearchWidget(
            posX = leftPos + GRID_LEFT,
            posY = topPos + SEARCH_TOP,
            width = SEARCH_WIDTH,
            height = SEARCH_HEIGHT,
            update = {}
        ).also { widget ->
            widget.setMaxLength(128)
            widget.value = searchQuery
            widget.setResponder { text ->
                searchQuery = text
                scrollOffsetRows = 0
            }
            addRenderableWidget(widget)
        }

        printButton = addRenderableWidget(
            Button.builder(Component.translatable("cobblemon.ui.photo_printer.print")) {
                CraftPrintedPhotoPacket(menu.blockPos(), selectedPhotoId).sendToServer()
            }.pos(leftPos + PRINT_BUTTON_X, topPos + PRINT_BUTTON_Y).size(PRINT_BUTTON_WIDTH, PRINT_BUTTON_HEIGHT).build()
        )
        CobblemonClient.pokedexPhotoStore.requestGallery()
    }

    override fun containerTick() {
        super.containerTick()
        searchQuery = searchWidget?.value ?: searchQuery
        val allPhotos = CobblemonClient.pokedexPhotoStore.getPhotos()
        val filteredPhotos = filteredPhotos(allPhotos)
        filteredPhotoCount = filteredPhotos.size

        val previousSelection = selectedPhotoId
        selectedPhotoId = when {
            filteredPhotos.isEmpty() -> null
            selectedPhotoId == null -> filteredPhotos.first().id
            filteredPhotos.none { it.id == selectedPhotoId } -> filteredPhotos.first().id
            else -> selectedPhotoId
        }

        if (selectedPhotoId != previousSelection) {
            SetPhotoPrinterSelectionPacket(menu.blockPos(), selectedPhotoId).sendToServer()
        }

        printButton?.active = selectedPhotoId != null
    }

    override fun removed() {
        searchWidget = null
        CobblemonClient.pokedexPhotoStore.retain(emptySet())
        super.removed()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x0 = leftPos
        val y0 = topPos
        guiGraphics.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, -12566464)
        guiGraphics.fill(x0 + 1, y0 + 1, x0 + imageWidth - 1, y0 + imageHeight - 1, -4144960)
        guiGraphics.fill(x0 + 2, y0 + 2, x0 + imageWidth - 2, y0 + imageHeight - 2, -6250336)

        guiGraphics.fill(
            leftPos + GRID_LEFT - 2,
            topPos + GRID_TOP - 2,
            leftPos + GRID_LEFT + GRID_WIDTH + 2,
            topPos + GRID_TOP + GRID_HEIGHT + 2,
            -12698050
        )
        guiGraphics.fill(
            leftPos + GRID_LEFT - 1,
            topPos + GRID_TOP - 1,
            leftPos + GRID_LEFT + GRID_WIDTH + 1,
            topPos + GRID_TOP + GRID_HEIGHT + 1,
            -3552823
        )
        guiGraphics.fill(
            leftPos + GRID_LEFT,
            topPos + GRID_TOP,
            leftPos + GRID_LEFT + GRID_WIDTH,
            topPos + GRID_TOP + GRID_HEIGHT,
            -986896
        )

        drawSlotOutline(guiGraphics, leftPos + INPUT_SLOT_X, topPos + INPUT_SLOT_Y)
        drawSlotOutline(guiGraphics, leftPos + OUTPUT_SLOT_X, topPos + OUTPUT_SLOT_Y)
        drawPanel(
            guiGraphics = guiGraphics,
            x = leftPos + PREVIEW_LEFT,
            y = topPos + PREVIEW_TOP,
            width = PREVIEW_SIZE,
            height = PREVIEW_SIZE
        )

        val gridStartX = leftPos + GRID_LEFT
        val gridStartY = topPos + GRID_TOP
        val allPhotos = CobblemonClient.pokedexPhotoStore.getPhotos()
        val photos = filteredPhotos(allPhotos)
        filteredPhotoCount = photos.size
        val totalRows = (photos.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        val maxOffset = maxOf(0, totalRows - GRID_ROWS)
        scrollOffsetRows = scrollOffsetRows.coerceIn(0, maxOffset)

        val visibleStart = scrollOffsetRows * GRID_COLUMNS
        val visibleEnd = minOf(photos.size, visibleStart + MAX_THUMBS)
        val visiblePhotos = photos.subList(visibleStart, visibleEnd)

        val bufferStartRow = maxOf(0, scrollOffsetRows - BUFFER_ROWS)
        val bufferEndRow = minOf(totalRows, scrollOffsetRows + GRID_ROWS + BUFFER_ROWS)
        val bufferStartIndex = bufferStartRow * GRID_COLUMNS
        val bufferEndIndex = minOf(photos.size, bufferEndRow * GRID_COLUMNS)
        val retainedIds = photos.subList(bufferStartIndex, bufferEndIndex).map { it.id }.toMutableSet()
        selectedPhotoId?.let { retainedIds.add(it) }
        CobblemonClient.pokedexPhotoStore.prefetch(retainedIds)
        CobblemonClient.pokedexPhotoStore.retain(retainedIds)

        visiblePhotos.forEachIndexed { index, photo ->
            val col = index % GRID_COLUMNS
            val row = index / GRID_COLUMNS
            val thumbX = gridStartX + col * (THUMB_SIZE + THUMB_GAP)
            val thumbY = gridStartY + row * (THUMB_SIZE + THUMB_GAP)

            val selected = photo.id == selectedPhotoId
            val borderColor = if (selected) -8704 else -12698050
            guiGraphics.fill(thumbX - 1, thumbY - 1, thumbX + THUMB_SIZE + 1, thumbY + THUMB_SIZE + 1, borderColor)

            val texture = CobblemonClient.pokedexPhotoStore.getTexture(photo.id)
            if (texture != null) {
                blitk(guiGraphics.pose(), texture, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE)
            } else {
                guiGraphics.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, -12040120)
            }
        }

        if (photos.isEmpty()) {
            guiGraphics.drawString(
                font,
                Component.translatable("cobblemon.ui.photo_printer.no_match"),
                leftPos + GRID_LEFT + 12,
                topPos + GRID_TOP + (GRID_HEIGHT / 2) - 4,
                0x3F3F3F,
                false
            )
        }

        renderSelectedPreview(guiGraphics)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, title, 8, 6, 4210752, false)
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 4210752, false)

        val total = CobblemonClient.pokedexPhotoStore.getPhotos().size
        val maxAllowed = CobblemonClient.pokedexPhotoStore.maxPhotosAllowed
        guiGraphics.drawString(font, Component.translatable("cobblemon.ui.photo_printer.count", total, maxAllowed), INFO_LEFT, INFO_TOP, 4210752, false)
        guiGraphics.drawString(font, Component.translatable("cobblemon.ui.photo_printer.filtered", filteredPhotoCount, total), INFO_LEFT, INFO_TOP + 11, 4210752, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (hoveredSlot != null && super.mouseClicked(mouseX, mouseY, button)) {
            return true
        }

        val filteredPhotos = filteredPhotos(CobblemonClient.pokedexPhotoStore.getPhotos())
        val visibleStart = scrollOffsetRows * GRID_COLUMNS
        val visibleEnd = minOf(filteredPhotos.size, visibleStart + MAX_THUMBS)
        val visiblePhotos = filteredPhotos.subList(visibleStart, visibleEnd)
        val selected = photoAt(mouseX, mouseY, visiblePhotos)
        if (selected != null) {
            selectedPhotoId = selected.id
            SetPhotoPrinterSelectionPacket(menu.blockPos(), selected.id).sendToServer()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        val gridLeft = leftPos + GRID_LEFT
        val gridTop = topPos + GRID_TOP
        val gridRight = gridLeft + GRID_COLUMNS * THUMB_SIZE + (GRID_COLUMNS - 1) * THUMB_GAP
        val gridBottom = gridTop + GRID_ROWS * THUMB_SIZE + (GRID_ROWS - 1) * THUMB_GAP
        if (mouseX < gridLeft || mouseX >= gridRight || mouseY < gridTop || mouseY >= gridBottom) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)
        }

        val step = if (deltaY > 0) -1 else if (deltaY < 0) 1 else 0
        if (step == 0) return true

        val totalPhotos = filteredPhotos(CobblemonClient.pokedexPhotoStore.getPhotos()).size
        val totalRows = (totalPhotos + GRID_COLUMNS - 1) / GRID_COLUMNS
        val maxOffset = maxOf(0, totalRows - GRID_ROWS)
        val newOffset = (scrollOffsetRows + step).coerceIn(0, maxOffset)
        if (newOffset == scrollOffsetRows) return true
        scrollOffsetRows = newOffset
        return true
    }

    private fun filteredPhotos(photos: List<PokedexPhotoMetadata>): List<PokedexPhotoMetadata> {
        return PokedexPhotoSearch.filter(photos, searchQuery)
    }

    private fun drawSlotOutline(guiGraphics: GuiGraphics, x: Int, y: Int) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, -12698050)
        guiGraphics.fill(x, y, x + 16, y + 16, -3552823)
        guiGraphics.fill(x + 1, y + 1, x + 16, y + 16, -986896)
    }

    private fun drawPanel(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, -12698050)
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, -3552823)
        guiGraphics.fill(x, y, x + width, y + height, -986896)
    }

    private fun renderSelectedPreview(guiGraphics: GuiGraphics) {
        val selected = selectedPhotoId ?: return
        val texture = CobblemonClient.pokedexPhotoStore.getTexture(selected) ?: return
        blitk(
            matrixStack = guiGraphics.pose(),
            texture = texture,
            x = leftPos + PREVIEW_LEFT,
            y = topPos + PREVIEW_TOP,
            width = PREVIEW_SIZE,
            height = PREVIEW_SIZE
        )
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
