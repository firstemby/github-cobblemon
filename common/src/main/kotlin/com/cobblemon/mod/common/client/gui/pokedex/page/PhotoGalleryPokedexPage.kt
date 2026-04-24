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
import com.cobblemon.mod.common.client.gui.pokedex.widgets.SearchWidget
import com.cobblemon.mod.common.client.pokedex.photo.PokedexPhotoSearch
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.client.ui.DeletePokedexPhotoPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.max
import kotlin.math.min

class PhotoGalleryPokedexPage(
    override val id: ResourceLocation,
    override val titleKey: String
) : PokedexGuiPage {
    companion object {
        private const val GRID_COLUMNS = 4
        private const val VISIBLE_ROWS = 3
        private const val BUFFER_ROWS = 1
        private const val THUMB_WIDTH = 36
        private const val THUMB_HEIGHT = 36
        private const val THUMB_GAP = 4
        private const val GRID_OFFSET_X = 28
        private const val GRID_OFFSET_Y = 49
        private const val SEARCH_OFFSET_X = 26
        private const val SEARCH_OFFSET_Y = 28
        private const val SEARCH_WIDTH = 120
        private const val SEARCH_HEIGHT = 17
        private const val PREVIEW_OFFSET_X = 218
        private const val PREVIEW_OFFSET_Y = 43
        private const val PREVIEW_SIZE = 112
    }

    private var scrollOffsetRows = 0
    private var selectedPhotoId: String? = null
    private var searchQuery = ""
    private var searchWidget: SearchWidget? = null

    override fun onSelected(gui: PokedexGUI) {
        scrollOffsetRows = 0
        selectedPhotoId = null
        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2
        searchWidget?.let { gui.removePageWidget(it) }
        searchWidget = SearchWidget(
            posX = x + SEARCH_OFFSET_X,
            posY = y + SEARCH_OFFSET_Y,
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
            gui.addPageWidget(widget)
        }
        CobblemonClient.pokedexPhotoStore.requestGallery()
    }

    override fun onClose(gui: PokedexGUI) {
        searchWidget?.let { gui.removePageWidget(it) }
        searchWidget = null
        CobblemonClient.pokedexPhotoStore.retain(emptySet())
    }

    override fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val photoStore = CobblemonClient.pokedexPhotoStore
        searchQuery = searchWidget?.value ?: searchQuery
        searchWidget?.render(context, mouseX, mouseY, delta)

        val allPhotos = photoStore.getPhotos()
        val photos = PokedexPhotoSearch.filter(allPhotos, searchQuery)

        val countText = lang("ui.pokedex.gallery.filtered_count", photos.size, allPhotos.size).bold()
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = countText,
            x = x + 173,
            y = y + 31,
            shadow = true,
            centered = true
        )

        if (allPhotos.isEmpty()) {
            selectedPhotoId = null
            photoStore.retain(emptySet())
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

        if (photos.isEmpty()) {
            selectedPhotoId = null
            photoStore.retain(emptySet())
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.pokedex.gallery.no_match").bold(),
                x = x + 173,
                y = y + 97,
                shadow = true,
                centered = true
            )
            return
        }

        if (selectedPhotoId == null || photos.none { it.id == selectedPhotoId }) {
            selectedPhotoId = photos.first().id
        }

        val totalRows = (photos.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        val maxOffset = max(0, totalRows - VISIBLE_ROWS)
        scrollOffsetRows = scrollOffsetRows.coerceIn(0, maxOffset)

        val bufferStartRow = max(0, scrollOffsetRows - BUFFER_ROWS)
        val bufferEndRow = min(totalRows, scrollOffsetRows + VISIBLE_ROWS + BUFFER_ROWS)
        val bufferStartIndex = bufferStartRow * GRID_COLUMNS
        val bufferEndIndex = min(photos.size, bufferEndRow * GRID_COLUMNS)
        val bufferIds = photos.subList(bufferStartIndex, bufferEndIndex).map { it.id }.toMutableList()
        selectedPhotoId?.let { selectedId ->
            if (!bufferIds.contains(selectedId)) {
                bufferIds.add(selectedId)
            }
        }
        photoStore.prefetch(bufferIds)
        photoStore.retain(bufferIds.toSet())

        val startX = x + GRID_OFFSET_X
        val startY = y + GRID_OFFSET_Y
        val visibleStart = scrollOffsetRows * GRID_COLUMNS
        val visibleEnd = min(photos.size, (scrollOffsetRows + VISIBLE_ROWS) * GRID_COLUMNS)

        for (index in visibleStart until visibleEnd) {
            val photo = photos[index]
            val localIndex = index - visibleStart
            val col = localIndex % GRID_COLUMNS
            val row = localIndex / GRID_COLUMNS
            val thumbX = startX + (col * (THUMB_WIDTH + THUMB_GAP))
            val thumbY = startY + (row * (THUMB_HEIGHT + THUMB_GAP))
            val selected = photo.id == selectedPhotoId

            if (selected) {
                context.fill(thumbX - 2, thumbY - 2, thumbX + THUMB_WIDTH + 2, thumbY + THUMB_HEIGHT + 2, -1)
            }

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
            }
        }

        selectedPhotoId?.let { selectedId ->
            val texture = photoStore.getTexture(selectedId)
            if (texture != null) {
                val previewX = x + PREVIEW_OFFSET_X
                val previewY = y + PREVIEW_OFFSET_Y
                context.fill(previewX - 2, previewY - 2, previewX + PREVIEW_SIZE + 2, previewY + PREVIEW_SIZE + 2, -1)
                blitk(
                    matrixStack = context.pose(),
                    texture = texture,
                    x = previewX,
                    y = previewY,
                    width = PREVIEW_SIZE,
                    height = PREVIEW_SIZE
                )
            }
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokedex.gallery.filter_hint"),
            x = x + 173,
            y = y + 158,
            shadow = true,
            centered = true
        )
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokedex.gallery.select_hint"),
            x = x + 173,
            y = y + 168,
            shadow = true,
            centered = true
        )
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokedex.gallery.delete_hint"),
            x = x + 173,
            y = y + 178,
            shadow = true,
            centered = true
        )
    }

    override fun mouseClicked(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean {
        searchWidget?.let { widget ->
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                gui.focusPageWidget(widget)
                return true
            }
        }

        if (button != 0 && button != 1) return false

        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2
        val photos = PokedexPhotoSearch.filter(CobblemonClient.pokedexPhotoStore.getPhotos(), searchQuery)
        if (photos.isEmpty()) return false
        val visibleStart = scrollOffsetRows * GRID_COLUMNS
        val visibleEnd = min(photos.size, (scrollOffsetRows + VISIBLE_ROWS) * GRID_COLUMNS)
        val visiblePhotos = photos.subList(visibleStart, visibleEnd)

        val photo = photoAt(mouseX, mouseY, x, y, visiblePhotos) ?: return false
        if (button == 1) {
            DeletePokedexPhotoPacket(photo.id).sendToServer()
            if (selectedPhotoId == photo.id) {
                selectedPhotoId = null
            }
        } else {
            selectedPhotoId = photo.id
        }
        return true
    }

    override fun keyPressed(gui: PokedexGUI, keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return searchWidget?.keyPressed(keyCode, scanCode, modifiers) == true
    }

    override fun charTyped(gui: PokedexGUI, codePoint: Char, modifiers: Int): Boolean {
        return searchWidget?.charTyped(codePoint, modifiers) == true
    }

    override fun mouseScrolled(gui: PokedexGUI, mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2
        val gridLeft = x + GRID_OFFSET_X
        val gridTop = y + GRID_OFFSET_Y
        val gridRight = gridLeft + GRID_COLUMNS * THUMB_WIDTH + (GRID_COLUMNS - 1) * THUMB_GAP
        val gridBottom = gridTop + VISIBLE_ROWS * THUMB_HEIGHT + (VISIBLE_ROWS - 1) * THUMB_GAP
        if (mouseX < gridLeft || mouseX >= gridRight || mouseY < gridTop || mouseY >= gridBottom) return false

        val step = if (deltaY > 0) -1 else if (deltaY < 0) 1 else 0
        if (step == 0) return false
        val photos = PokedexPhotoSearch.filter(CobblemonClient.pokedexPhotoStore.getPhotos(), searchQuery)
        val totalRows = (photos.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        val maxOffset = max(0, totalRows - VISIBLE_ROWS)
        scrollOffsetRows = (scrollOffsetRows + step).coerceIn(0, maxOffset)
        return true
    }

    private fun photoAt(
        mouseX: Double,
        mouseY: Double,
        x: Int,
        y: Int,
        visiblePhotos: List<PokedexPhotoMetadata>
    ): PokedexPhotoMetadata? {
        val startX = x + GRID_OFFSET_X
        val startY = y + GRID_OFFSET_Y

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
