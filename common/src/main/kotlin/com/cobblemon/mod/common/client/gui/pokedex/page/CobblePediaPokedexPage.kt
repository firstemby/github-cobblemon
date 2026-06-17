/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_WIDTH
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.structure.StructurePreviewLoader
import com.cobblemon.mod.common.client.render.structure.StructurePreviewRenderer
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import kotlin.math.max

/**
 * Pokédex page: a grid of [ScannedDataCategory] tiles. A category with sub-buckets drills into a
 * grid of sub-category tiles; otherwise it opens its scanned list directly. Reads
 * [CobblemonClient.clientScannedData].
 */
class CobblePediaPokedexPage(
    override val id: ResourceLocation,
    override val titleKey: String
) : PokedexGuiPage {
    companion object {
        private const val COLUMNS = 3
        private const val GRID_LEFT = 20
        private const val GRID_TOP = 42
        private const val GRID_WIDTH = 305
        private const val TILE_GAP = 8
        private const val TILE_HEIGHT = 34

        private const val LIST_LEFT = 28
        private const val LIST_TOP = 48
        private const val LIST_LINE_HEIGHT = 11
        private const val LIST_VISIBLE_ROWS = 11
        private const val BACK_X = 20
        private const val BACK_Y = 14   // raised vertically (was 26)
        private const val BACK_W = 16   // back-button sheet is 16x16 = two stacked 16x8 frames
        private const val BACK_H = 8    // one frame: top = idle, bottom = hovered
        private val BACK_BUTTON = cobblemonResource("textures/gui/pokedex/pokedex_back_button.png")

        private const val TILE_BG = 0xCC1A1A1A.toInt()
        private const val TILE_BG_HOVER = 0xCC3A3A3A.toInt()
        private const val TILE_BORDER = 0xFF7EE7E5.toInt()
        private const val BARE_SUB = "" // sentinel sub id for a category's bare bucket

        // Right-side structure preview pane (relative to the page origin x,y)
        private const val PREVIEW_X = 170
        private const val PREVIEW_Y = 42
        private const val PREVIEW_W = 155
        private const val PREVIEW_H = 130
        private const val PREVIEW_BG = 0xC0101010.toInt()
        private const val LIST_ROW_HIGHLIGHT = 0x803A3A3A.toInt()
        private const val LIST_ROW_WIDTH = 130

        // "Refresh" button overlaid at the preview pane's top-right: re-rolls processor substitutions.
        private const val REFRESH_W = 50
        private const val REFRESH_H = 12
        private const val REFRESH_BG = 0xCC1A1A1A.toInt()
        private const val REFRESH_BG_HOVER = 0xCC3A3A3A.toInt()

        private fun tileWidth(): Int = (GRID_WIDTH - TILE_GAP * (COLUMNS - 1)) / COLUMNS

        private fun categoryName(category: ScannedDataCategory): Component =
            lang("ui.pokedex.cobblepedia.category.${category.id}")

        private fun subName(sub: String): Component {
            if (sub == BARE_SUB) return lang("ui.pokedex.cobblepedia.subcategory.general")
            val fallback = sub.replace('_', ' ').replaceFirstChar { it.uppercase() }
            return Component.translatableWithFallback("cobblemon.ui.pokedex.cobblepedia.subcategory.$sub", fallback)
        }

        private fun entryName(entry: ResourceLocation): MutableComponent {
            val fallback = entry.path.replace('_', ' ').replaceFirstChar { it.uppercase() }
            return Component.translatableWithFallback("cobblemon.ui.pokedex.cobblepedia.entry.${entry.namespace}.${entry.path}", fallback)
        }
    }

    private var selectedCategory: ScannedDataCategory? = null
    private var selectedSub: String? = null // null = sub-grid (or direct list if no subs); non-null = a bucket's list
    private var scrollRow = 0

    // WorldDex (replaces the Habitats/Structures categories): a category submenu (Habitats/Ruins/
    // Fossils) leading into a per-category browser.
    private var worldDexMode = false
    private var worldDexCategory: WorldDexData.Category? = null
    private val worldDexView = WorldDexView()

    // BerryDex (opened from the Berries tile)
    private var berryDexMode = false
    private val berryDexView = BerryDexView()

    // MoveDex (comprehensive move browser)
    private var moveDexMode = false
    private val moveDexView = MoveDexView()

    // Structure preview pane state
    private var selectedEntry: ResourceLocation? = null
    private var previewYaw = -30f
    private var previewPitch = 30f
    private var previewZoom = 1f

    override fun onSelected(gui: PokedexGUI) {
        selectedCategory = null
        selectedSub = null
        scrollRow = 0
        worldDexMode = false
        worldDexCategory = null
        worldDexView.reset()
        berryDexMode = false
        berryDexView.reset()
        moveDexMode = false
        moveDexView.reset()
        clearSelectedEntry()
    }

    /** Categories shown as CobblePedia tiles; Habitats/Structures fold into the WorldDex; Eggs/Blocks are hidden. */
    private fun tileCategories(): List<ScannedDataCategory> = ScannedDataCategory.entries.filter {
        it != ScannedDataCategory.HABITATS && it != ScannedDataCategory.STRUCTURES &&
            it != ScannedDataCategory.EGGS && it != ScannedDataCategory.BLOCKS &&
            it != ScannedDataCategory.RUINS && it != ScannedDataCategory.FOSSILS
    }

    private fun clearSelectedEntry() {
        selectedEntry = null
        previewYaw = -30f
        previewPitch = 30f
        previewZoom = 1f
    }

    override fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        if (worldDexMode) {
            renderBackButton(context, x, y, mouseX, mouseY)
            val cat = worldDexCategory
            if (cat == null) {
                renderTileGrid(context, x, y, mouseX, mouseY,
                    Component.translatableWithFallback("cobblemon.ui.worlddex.title", "WorldDex"),
                    WorldDexData.Category.entries.map { Tile(it.label, WorldDexData.unlockedCount(it)) })
            } else {
                worldDexView.render(context, x, y, mouseX, mouseY)
            }
            return
        }
        if (berryDexMode) {
            renderBackButton(context, x, y, mouseX, mouseY)
            berryDexView.render(context, x, y, mouseX, mouseY)
            return
        }
        if (moveDexMode) {
            renderBackButton(context, x, y, mouseX, mouseY)
            moveDexView.render(context, x, y, mouseX, mouseY, delta)
            return
        }

        val category = selectedCategory
        if (category == null) {
            renderTileGrid(context, x, y, mouseX, mouseY, lang("ui.pokedex.page.cobblepedia"), hubTiles(gui).map { it.first })
            return
        }

        renderBackButton(context, x, y, mouseX, mouseY)

        if (selectedSub == null && hasSubcategories(category)) {
            renderTileGrid(context, x, y, mouseX, mouseY, categoryName(category), subTiles(category))
        } else {
            val title = if (selectedSub == null) categoryName(category) else subName(selectedSub!!)
            renderList(context, x, y, mouseX, mouseY, title.copy().bold(), listEntriesFor(category, selectedSub))
        }
    }

    // ---- tiles ----

    private data class Tile(val label: Component, val count: Int)

    /**
     * The top-level hub tiles in render order, each paired with its click action. Order: the main Pokédex,
     * the scanned-data categories (Berries/Items/Misc — Eggs/Blocks hidden), WorldDex, MoveDex, Photo Gallery.
     * A count of -1 renders no count line (used for the page-link tiles). [gui] is needed to switch top-level pages.
     */
    private fun hubTiles(gui: PokedexGUI): List<Pair<Tile, () -> Unit>> {
        val list = ArrayList<Pair<Tile, () -> Unit>>()
        list += Tile(Component.translatableWithFallback("cobblemon.ui.pokedex.page.pokedex", "Pokédex"), -1) to { gui.openPage(MainDexPokedexPage::class.java) }
        tileCategories().forEach { cat ->
            list += Tile(categoryName(cat), entriesForCategory(cat).size) to {
                if (cat == ScannedDataCategory.BERRIES) {
                    berryDexMode = true
                } else {
                    selectedCategory = cat; selectedSub = null; scrollRow = 0; clearSelectedEntry()
                }
            }
        }
        list += Tile(Component.translatableWithFallback("cobblemon.ui.worlddex.title", "WorldDex"),
            WorldDexData.Category.entries.sumOf { WorldDexData.unlockedCount(it) }) to { worldDexMode = true }
        list += Tile(Component.translatableWithFallback("cobblemon.ui.movedex.title", "MoveDex"), MoveDexData.discoveredCount()) to {
            moveDexMode = true; moveDexView.open()
        }
        list += Tile(Component.translatableWithFallback("cobblemon.ui.pokedex.page.photo_gallery", "Photo Gallery"), -1) to { gui.openPage(PhotoGalleryPokedexPage::class.java) }
        return list
    }

    private fun subTiles(category: ScannedDataCategory): List<Tile> =
        subKeys(category).map { sub -> Tile(subName(sub), bucketEntries(category.bucket(sub)).size) }

    private fun renderTileGrid(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, title: Component, tiles: List<Tile>) {
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = title.copy().bold(),
            x = x + (BASE_WIDTH / 2),
            y = y + 26,
            shadow = true,
            centered = true
        )

        val tileW = tileWidth()
        tiles.forEachIndexed { index, tile ->
            val (tileX, tileY) = tilePos(x, y, index, tileW)
            val hovered = mouseX in tileX..(tileX + tileW) && mouseY in tileY..(tileY + TILE_HEIGHT)
            context.fill(tileX, tileY, tileX + tileW, tileY + TILE_HEIGHT, if (hovered) TILE_BG_HOVER else TILE_BG)
            context.fill(tileX, tileY, tileX + tileW, tileY + 1, TILE_BORDER)
            context.fill(tileX, tileY + TILE_HEIGHT - 1, tileX + tileW, tileY + TILE_HEIGHT, TILE_BORDER)
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = tile.label.copy().bold(),
                x = tileX + tileW / 2,
                y = tileY + 9,
                shadow = true,
                centered = true
            )
            if (tile.count >= 0) {
                drawScaledText(
                    context = context,
                    text = lang("ui.pokedex.cobblepedia.count", tile.count),
                    x = tileX + tileW / 2,
                    y = tileY + 22,
                    shadow = true,
                    centered = true,
                    scale = 0.5F
                )
            }
        }
    }

    // ---- list ----

    private fun renderList(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, title: MutableComponent, entries: List<ResourceLocation>) {
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = title,
            x = x + (BASE_WIDTH / 2),
            y = y + 26,
            shadow = true,
            centered = true
        )

        if (entries.isEmpty()) {
            drawScaledText(context = context, text = lang("ui.pokedex.cobblepedia.empty"), x = x + LIST_LEFT, y = y + LIST_TOP, shadow = true)
            return
        }

        val maxScroll = max(0, entries.size - LIST_VISIBLE_ROWS)
        if (scrollRow > maxScroll) scrollRow = maxScroll

        val end = (scrollRow + LIST_VISIBLE_ROWS).coerceAtMost(entries.size)
        for (i in scrollRow until end) {
            val rowY = y + LIST_TOP + (i - scrollRow) * LIST_LINE_HEIGHT
            if (entries[i] == selectedEntry) {
                context.fill(x + LIST_LEFT - 2, rowY - 1, x + LIST_LEFT + LIST_ROW_WIDTH, rowY + LIST_LINE_HEIGHT - 2, LIST_ROW_HIGHLIGHT)
            }
            drawScaledText(
                context = context,
                text = entryName(entries[i]),
                x = x + LIST_LEFT,
                y = rowY,
                shadow = true
            )
        }

        // Right-side 3D preview of the selected entry's structure, if one exists.
        val sel = selectedEntry
        if (sel != null) {
            val preview = StructurePreviewLoader.forEntry(sel)
            if (preview != null) {
                context.fill(x + PREVIEW_X, y + PREVIEW_Y, x + PREVIEW_X + PREVIEW_W, y + PREVIEW_Y + PREVIEW_H, PREVIEW_BG)
                StructurePreviewRenderer.render(
                    context, preview,
                    x + PREVIEW_X, y + PREVIEW_Y, PREVIEW_W, PREVIEW_H,
                    previewYaw, previewPitch, previewZoom
                )
                renderRefreshButton(context, x, y, mouseX, mouseY)
            }
        }
    }

    private fun renderRefreshButton(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val bx = x + PREVIEW_X + PREVIEW_W - REFRESH_W - 2
        val by = y + PREVIEW_Y + 2
        val hovered = mouseX in bx..(bx + REFRESH_W) && mouseY in by..(by + REFRESH_H)
        context.fill(bx, by, bx + REFRESH_W, by + REFRESH_H, if (hovered) REFRESH_BG_HOVER else REFRESH_BG)
        drawScaledText(
            context = context,
            text = Component.translatableWithFallback("cobblemon.ui.pokedex.cobblepedia.refresh", "Refresh"),
            x = bx + REFRESH_W / 2,
            y = by + 3,
            shadow = true,
            centered = true,
            scale = 0.5F
        )
    }

    private fun renderBackButton(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val bx = x + BACK_X
        val by = y + BACK_Y
        val hovered = mouseX in bx..(bx + BACK_W) && mouseY in by..(by + BACK_H)
        // 16x16 sheet = two vertically-stacked 16x8 frames: top half (vOffset 0) idle, bottom half (vOffset 8) hover.
        blitk(
            matrixStack = context.pose(),
            texture = BACK_BUTTON,
            x = bx, y = by,
            width = BACK_W, height = BACK_H,
            vOffset = if (hovered) BACK_H else 0,
            textureHeight = BACK_H * 2
        )
    }

    // ---- input ----

    override fun mouseClicked(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean {
        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2

        if (worldDexMode) {
            if (isBackClicked(x, y, mouseX, mouseY)) {
                // Back from a category returns to the category submenu; from the submenu, exits the WorldDex.
                if (worldDexCategory != null) worldDexCategory = null else worldDexMode = false
                worldDexView.reset()
                return true
            }
            if (worldDexCategory == null) {
                val index = tileIndexAt(x, y, mouseX, mouseY, WorldDexData.Category.entries.size) ?: return false
                val cat = WorldDexData.Category.entries[index]
                worldDexCategory = cat
                worldDexView.open(cat)
                return true
            }
            return worldDexView.mouseClicked(x, y, mouseX, mouseY)
        }
        if (berryDexMode) {
            if (isBackClicked(x, y, mouseX, mouseY)) {
                berryDexMode = false
                berryDexView.reset()
                return true
            }
            return berryDexView.mouseClicked(x, y, mouseX, mouseY)
        }
        if (moveDexMode) {
            if (isBackClicked(x, y, mouseX, mouseY)) {
                moveDexMode = false
                moveDexView.reset()
                return true
            }
            return moveDexView.mouseClicked(x, y, mouseX, mouseY)
        }

        val category = selectedCategory

        if (category == null) {
            val tiles = hubTiles(gui)
            val index = tileIndexAt(x, y, mouseX, mouseY, tiles.size) ?: return false
            tiles[index].second()
            return true
        }

        if (isBackClicked(x, y, mouseX, mouseY)) {
            if (selectedSub != null && hasSubcategories(category)) selectedSub = null else selectedCategory = null
            scrollRow = 0
            clearSelectedEntry()
            return true
        }

        if (selectedSub == null && hasSubcategories(category)) {
            val subs = subKeys(category)
            val index = tileIndexAt(x, y, mouseX, mouseY, subs.size) ?: return false
            selectedSub = subs[index]
            scrollRow = 0
            clearSelectedEntry()
            return true
        }

        // Refresh button (only present while a preview is shown): re-roll the structure's substitutions.
        if (previewVisible() && isRefreshClicked(x, y, mouseX, mouseY)) {
            selectedEntry?.let { StructurePreviewLoader.reroll(it) }
            return true
        }

        // List view: clicking a row selects that entry (and shows its structure preview if any).
        val entries = listEntriesFor(category, selectedSub)
        val rowIndex = rowIndexAt(x, y, mouseX, mouseY, entries.size)
        if (rowIndex != null) {
            selectedEntry = entries[rowIndex]
            previewYaw = -30f
            previewPitch = 30f
            previewZoom = 1f
            return true
        }
        return false
    }

    override fun keyPressed(gui: PokedexGUI, keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (worldDexMode && worldDexCategory != null) return worldDexView.keyPressed(keyCode)
        if (berryDexMode) return berryDexView.keyPressed(keyCode)
        if (moveDexMode) return moveDexView.keyPressed(keyCode)
        return false
    }

    override fun charTyped(gui: PokedexGUI, codePoint: Char, modifiers: Int): Boolean {
        if (worldDexMode && worldDexCategory != null) return worldDexView.charTyped(codePoint)
        if (berryDexMode) return berryDexView.charTyped(codePoint)
        if (moveDexMode) return moveDexView.charTyped(codePoint)
        return false
    }

    override fun mouseDragged(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button != 0) return false
        if (worldDexMode) return worldDexCategory != null && worldDexView.mouseDragged(deltaX, deltaY)
        if (berryDexMode) return berryDexView.mouseDragged(deltaX, deltaY)
        if (moveDexMode) return moveDexView.mouseDragged(deltaX, deltaY)
        if (!previewVisible()) return false
        previewYaw += deltaX.toFloat()
        previewPitch = (previewPitch + deltaY.toFloat()).coerceIn(-90f, 90f)
        return true
    }

    override fun mouseScrolled(gui: PokedexGUI, mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        if (worldDexMode) {
            if (worldDexCategory == null) return false
            val wx = (gui.width - BASE_WIDTH) / 2
            val wy = (gui.height - BASE_HEIGHT) / 2
            return worldDexView.mouseScrolled(wx, wy, mouseX, mouseY, deltaY)
        }
        if (berryDexMode) {
            val bx = (gui.width - BASE_WIDTH) / 2
            val by = (gui.height - BASE_HEIGHT) / 2
            return berryDexView.mouseScrolled(bx, by, mouseX, mouseY, deltaY)
        }
        if (moveDexMode) {
            val mx = (gui.width - BASE_WIDTH) / 2
            val my = (gui.height - BASE_HEIGHT) / 2
            return moveDexView.mouseScrolled(mx, my, mouseX, mouseY, deltaY)
        }
        val category = selectedCategory ?: return false
        if (selectedSub == null && hasSubcategories(category)) return false

        // Scrolling over the preview pane zooms; elsewhere it scrolls the list.
        val x = (gui.width - BASE_WIDTH) / 2
        val y = (gui.height - BASE_HEIGHT) / 2
        if (previewVisible() && mouseX >= x + PREVIEW_X && mouseX <= x + PREVIEW_X + PREVIEW_W &&
            mouseY >= y + PREVIEW_Y && mouseY <= y + PREVIEW_Y + PREVIEW_H) {
            previewZoom = (previewZoom + deltaY.toFloat() * 0.1f).coerceIn(0.25f, 4f)
            return true
        }

        val count = listEntriesFor(category, selectedSub).size
        val maxScroll = max(0, count - LIST_VISIBLE_ROWS)
        scrollRow = (scrollRow - deltaY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    // ---- data helpers ----

    private fun hasSubcategories(category: ScannedDataCategory): Boolean =
        CobblemonClient.clientScannedData.scanned.keys.any { it.startsWith("${category.id}/") }

    /** Sub ids with entries under a category, with the bare bucket first if it has entries. */
    private fun subKeys(category: ScannedDataCategory): List<String> {
        val keys = CobblemonClient.clientScannedData.scanned
        val result = mutableListOf<String>()
        if (keys[category.id]?.isNotEmpty() == true) result.add(BARE_SUB)
        keys.keys
            .filter { it.startsWith("${category.id}/") }
            .map { it.substringAfter('/') }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .forEach { result.add(it) }
        return result
    }

    private fun listEntriesFor(category: ScannedDataCategory, sub: String?): List<ResourceLocation> =
        if (sub == null) entriesForCategory(category).sortedBy { it.toString() }
        else bucketEntries(category.bucket(sub)).sortedBy { it.toString() }

    private fun bucketEntries(bucket: String): List<ResourceLocation> =
        CobblemonClient.clientScannedData.scanned[bucket]?.toList() ?: emptyList()

    private fun entriesForCategory(category: ScannedDataCategory): List<ResourceLocation> {
        val scanned = CobblemonClient.clientScannedData.scanned
            .filterKeys { ScannedDataCategory.categoryOfBucket(it) == category }
            .values.flatten()
        // TEMPORARY: all habitats and structures are treated as unlocked for testing the page + preview.
        return when (category) {
            ScannedDataCategory.HABITATS -> (scanned + TestUnlockedHabitats.entries).distinct()
            ScannedDataCategory.STRUCTURES -> (scanned + TestUnlockedStructures.entries).distinct()
            else -> scanned.distinct()
        }
    }

    // ---- geometry ----

    private fun tilePos(x: Int, y: Int, index: Int, tileW: Int): Pair<Int, Int> {
        val col = index % COLUMNS
        val row = index / COLUMNS
        return (x + GRID_LEFT + col * (tileW + TILE_GAP)) to (y + GRID_TOP + row * (TILE_HEIGHT + TILE_GAP))
    }

    private fun tileIndexAt(x: Int, y: Int, mouseX: Double, mouseY: Double, count: Int): Int? {
        val tileW = tileWidth()
        for (index in 0 until count) {
            val (tileX, tileY) = tilePos(x, y, index, tileW)
            if (mouseX >= tileX && mouseX <= tileX + tileW && mouseY >= tileY && mouseY <= tileY + TILE_HEIGHT) return index
        }
        return null
    }

    private fun isBackClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        val bx = x + BACK_X
        val by = y + BACK_Y
        return mouseX >= bx && mouseX <= bx + BACK_W && mouseY >= by && mouseY <= by + BACK_H
    }

    /** Which visible list row (absolute index into entries) is under the cursor, or null. */
    private fun rowIndexAt(x: Int, y: Int, mouseX: Double, mouseY: Double, count: Int): Int? {
        if (mouseX < x + LIST_LEFT - 2 || mouseX > x + LIST_LEFT + LIST_ROW_WIDTH) return null
        val end = (scrollRow + LIST_VISIBLE_ROWS).coerceAtMost(count)
        for (i in scrollRow until end) {
            val rowY = y + LIST_TOP + (i - scrollRow) * LIST_LINE_HEIGHT
            if (mouseY >= rowY - 1 && mouseY <= rowY + LIST_LINE_HEIGHT - 2) return i
        }
        return null
    }

    private fun isRefreshClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        val bx = x + PREVIEW_X + PREVIEW_W - REFRESH_W - 2
        val by = y + PREVIEW_Y + 2
        return mouseX >= bx && mouseX <= bx + REFRESH_W && mouseY >= by && mouseY <= by + REFRESH_H
    }

    /** True when a structure preview is currently being shown for the selected entry. */
    private fun previewVisible(): Boolean = selectedEntry?.let { StructurePreviewLoader.hasPreview(it) } == true
}
