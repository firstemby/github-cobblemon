/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.block.chest.GildedChestBlock
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.client.render.structure.StructurePreviewLoader
import com.cobblemon.mod.common.client.render.structure.StructurePreviewRenderer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max

/**
 * One WorldDex category page (Habitats, Ruins, or Fossils): a scrollable flat list of that category's
 * entries on the left, and a detail pane on the right with the drag-rotatable .nbt preview (variations
 * cycled with arrows) plus category-specific tabs:
 *  - Habitats: Spawns (per-habitat spawn pool, by rarity bucket) + Biomes.
 *  - Ruins:    Loot + Biomes.
 *  - Fossils:  Loot + Sus (suspicious gravel/sand finds) + Biomes.
 *
 * Placeholder flat-colour styling; real Pokédex textures are a later art pass.
 */
class WorldDexView {
    companion object {
        private const val LIST_X = 18
        private const val LIST_W = 142
        private const val LIST_TOP = 42
        private const val ROW_H = 12
        private const val VISIBLE_ROWS = 11
        private const val SEARCH_Y = 26
        private const val SEARCH_H = 12

        private const val DETAIL_X = 166
        private const val DETAIL_W = 160
        private const val HEADER_Y = 24
        private const val HEADER_H = 12
        private const val VIEW_Y = 39
        private const val VIEW_H = 72
        private const val ARROW_W = 10
        private const val TABS_Y = 114
        private const val TAB_H = 11
        private const val SUBTAB_Y = 128
        private const val SUBTAB_H = 10
        private const val CONTENT_BOTTOM = 188
        private const val ROW_TEXT_H = 9

        private const val PANEL_BG = 0xCC10303A.toInt()
        private const val PLATE = 0xFFE9F6F6.toInt()
        private const val PLATE_HOVER = 0xFFC8E8E8.toInt()
        private const val PLATE_SELECTED = 0xFFA9DCDC.toInt()
        private const val ICON_BG = 0xFF2E6F8C.toInt()
        private const val BORDER = 0xFF7EE7E5.toInt()
        private const val UNLOCK_DOT = 0xFF49C7C2.toInt()
        private const val TEXT_DARK = 0x33586B
        private const val PREVIEW_BG = 0xC0101010.toInt()

        private fun fallback(key: String, text: String) = Component.translatableWithFallback("cobblemon.ui.worlddex.$key", text)
    }

    private enum class Tab(val key: String, val label: String) {
        SPAWNS("tab.spawns", "Spawns"),
        LOOT("tab.loot", "Loot"),
        SUS("tab.sus", "Sus Dig"),
        BIOMES("tab.biomes", "Biomes")
    }

    // Biomes tab intentionally omitted for now.
    private fun tabsFor(category: WorldDexData.Category): List<Tab> = when (category) {
        WorldDexData.Category.HABITATS -> listOf(Tab.SPAWNS)
        WorldDexData.Category.RUINS -> listOf(Tab.LOOT)
        WorldDexData.Category.FOSSILS -> listOf(Tab.LOOT, Tab.SUS)
    }

    private var category = WorldDexData.Category.HABITATS
    private var scrollRow = 0
    private var selected: WorldDexData.Entry? = null
    private var selectedNumber = 0
    private var variantIndex = 0
    private var activeTab = Tab.SPAWNS
    private var contentScroll = 0
    private var spawnBucket: String? = null
    private var searchQuery = ""
    private var searchFocused = false
    private var yaw = -30f
    private var pitch = 30f
    private var zoom = 1f

    private val chestCache = HashMap<ResourceLocation, Boolean>()

    fun open(category: WorldDexData.Category) {
        this.category = category
        reset()
    }

    fun reset() {
        scrollRow = 0
        selected = null
        selectedNumber = 0
        variantIndex = 0
        activeTab = tabsFor(category).first()
        contentScroll = 0
        spawnBucket = null
        searchQuery = ""
        searchFocused = false
        resetView()
    }

    private fun resetView() {
        yaw = -30f; pitch = 30f; zoom = 1f
    }

    private fun entries(): List<WorldDexData.Entry> {
        val all = WorldDexData.entriesOf(category)
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return all
        return all.filter { entry ->
            entry.name.string.lowercase().contains(query) ||
                entry.biomeTags.any { it.path.contains(query) } ||
                (category == WorldDexData.Category.HABITATS &&
                    WorldDexHabitatSpawns.spawnsFor(entry).any { it.species.lowercase().contains(query) })
        }
    }

    // ---- rendering ----

    fun render(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        renderSearchBar(context, x, y, mouseX, mouseY)
        renderList(context, x, y, mouseX, mouseY)
        renderDetail(context, x, y, mouseX, mouseY)
    }

    private fun renderSearchBar(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val left = x + LIST_X
        val top = y + SEARCH_Y
        val hovered = mouseX in left..(left + LIST_W) && mouseY in top..(top + SEARCH_H)
        context.fill(left, top, left + LIST_W, top + SEARCH_H, if (searchFocused || hovered) PLATE_HOVER else PLATE)
        val text: MutableComponent = if (searchQuery.isEmpty() && !searchFocused) fallback("search", "Search")
        else Component.literal(searchQuery + if (searchFocused) "_" else "")
        drawScaledText(context = context, text = text, x = left + 3, y = top + 3, colour = TEXT_DARK, scale = 0.65F)
    }

    private fun renderList(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val list = entries()
        val maxScroll = max(0, list.size - VISIBLE_ROWS)
        if (scrollRow > maxScroll) scrollRow = maxScroll
        val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(list.size)
        for (i in scrollRow until end) {
            val entry = list[i]
            val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
            val left = x + LIST_X
            val plateLeft = left + 13
            val plateRight = left + LIST_W - 12
            val hovered = mouseX in left..(left + LIST_W) && mouseY in rowY..(rowY + ROW_H - 2)
            // icon plate (placeholder) + name plate + unlock dot
            context.fill(left, rowY, left + 11, rowY + 11, ICON_BG)
            context.fill(left, rowY, left + 11, rowY + 1, BORDER)
            context.fill(plateLeft, rowY, plateRight, rowY + 11, when {
                entry === selected -> PLATE_SELECTED
                hovered -> PLATE_HOVER
                else -> PLATE
            })
            drawScaledText(context = context, text = Component.literal("%04d".format(i + 1)), x = plateLeft + 2, y = rowY + 3, colour = TEXT_DARK, scale = 0.5F)
            drawScaledText(context = context, text = entry.name.copy().bold(), x = plateLeft + 22, y = rowY + 3, colour = TEXT_DARK, scale = 0.65F)
            context.fill(left + LIST_W - 9, rowY + 2, left + LIST_W - 2, rowY + 9, UNLOCK_DOT)
        }
    }

    private fun renderDetail(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val entry = selected ?: return
        val left = x + DETAIL_X

        context.fill(left, y + HEADER_Y, left + DETAIL_W, y + HEADER_Y + HEADER_H, PLATE)
        drawScaledText(context = context, text = Component.literal("%04d".format(selectedNumber)).bold(), x = left + 3, y = y + HEADER_Y + 3, colour = TEXT_DARK, scale = 0.65F)
        drawScaledText(context = context, text = entry.name.copy().bold(), x = left + 28, y = y + HEADER_Y + 3, colour = TEXT_DARK, scale = 0.75F)

        val variants = entry.variants
        if (variants.isEmpty()) {
            drawScaledText(context = context, text = fallback("no_preview", "No preview available"), x = left + DETAIL_W / 2, y = y + VIEW_Y + VIEW_H / 2, centered = true, shadow = true, scale = 0.65F)
            return
        }
        if (variantIndex >= variants.size) variantIndex = 0

        val viewLeft = left + ARROW_W + 2
        val viewWidth = DETAIL_W - (ARROW_W + 2) * 2
        context.fill(viewLeft, y + VIEW_Y, viewLeft + viewWidth, y + VIEW_Y + VIEW_H, PREVIEW_BG)
        StructurePreviewLoader.forEntry(variants[variantIndex])?.let {
            StructurePreviewRenderer.render(context, it, viewLeft, y + VIEW_Y, viewWidth, VIEW_H, yaw, pitch, zoom)
        }
        if (variants.size > 1) {
            renderArrow(context, left, y, mouseX, mouseY, right = false)
            renderArrow(context, left + DETAIL_W - ARROW_W, y, mouseX, mouseY, right = true)
            drawScaledText(context = context, text = Component.translatableWithFallback("cobblemon.ui.worlddex.variation", "Variation %s", variantIndex + 1), x = left + DETAIL_W / 2, y = y + VIEW_Y + VIEW_H - 8, centered = true, shadow = true, scale = 0.55F)
        }
        if (hasGimmighoulChest(variants[variantIndex])) {
            renderScaledGuiItemIcon(chestIconStack(), (left + DETAIL_W - 12).toDouble(), (y + HEADER_Y + 1).toDouble(), matrixStack = context.pose(), scale = 0.65)
        }

        // Category tabs.
        val tabs = tabsFor(category)
        val tabW = (DETAIL_W - (tabs.size - 1) * 2) / tabs.size
        tabs.forEachIndexed { index, tab ->
            val tx = left + index * (tabW + 2)
            val hovered = mouseX in tx..(tx + tabW) && mouseY in (y + TABS_Y)..(y + TABS_Y + TAB_H)
            context.fill(tx, y + TABS_Y, tx + tabW, y + TABS_Y + TAB_H, if (tab == activeTab) PLATE_SELECTED else if (hovered) PLATE_HOVER else PLATE)
            drawScaledText(context = context, text = fallback(tab.key, tab.label), x = tx + tabW / 2, y = y + TABS_Y + 3, colour = TEXT_DARK, centered = true, scale = 0.55F)
        }

        when (activeTab) {
            Tab.SPAWNS -> renderSpawns(context, left, y, entry, mouseX, mouseY)
            Tab.LOOT -> renderLoot(context, left, y, entry, sus = false)
            Tab.SUS -> renderLoot(context, left, y, entry, sus = true)
            Tab.BIOMES -> renderBiomes(context, left, y, entry)
        }
    }

    private fun renderArrow(context: GuiGraphics, ax: Int, y: Int, mouseX: Int, mouseY: Int, right: Boolean) {
        val top = y + VIEW_Y + VIEW_H / 2 - 8
        val hovered = mouseX in ax..(ax + ARROW_W) && mouseY in top..(top + 16)
        context.fill(ax, top, ax + ARROW_W, top + 16, if (hovered) PLATE_HOVER else PLATE)
        drawScaledText(context = context, text = Component.literal(if (right) ">" else "<").bold(), x = ax + ARROW_W / 2, y = top + 5, colour = TEXT_DARK, centered = true)
    }

    // ---- tab bodies ----

    private fun renderSpawns(context: GuiGraphics, left: Int, y: Int, entry: WorldDexData.Entry, mouseX: Int, mouseY: Int) {
        val spawns = WorldDexHabitatSpawns.spawnsFor(entry)
        if (spawns.isEmpty()) {
            panel(context, left, y, TABS_Y + TAB_H + 1)
            centeredMessage(context, left, y, TABS_Y + TAB_H + 1, fallback("no_spawns_yet", "No spawns yet"))
            return
        }
        // Rarity bucket sub-buttons.
        val buckets = spawns.map { it.bucket }.distinct().sortedBy { WorldDexHabitatSpawns.bucketOrder(it) }
        val current = spawnBucket?.takeIf { it in buckets } ?: buckets.first()
        val buttonW = (DETAIL_W - (buckets.size - 1) * 2) / buckets.size
        buckets.forEachIndexed { index, bucket ->
            val tx = left + index * (buttonW + 2)
            val hovered = mouseX in tx..(tx + buttonW) && mouseY in (y + SUBTAB_Y)..(y + SUBTAB_Y + SUBTAB_H)
            context.fill(tx, y + SUBTAB_Y, tx + buttonW, y + SUBTAB_Y + SUBTAB_H, if (bucket == current) PLATE_SELECTED else if (hovered) PLATE_HOVER else PLATE)
            drawScaledText(context = context, text = Component.literal(spawns.first { it.bucket == bucket }.bucketLabel), x = tx + buttonW / 2, y = y + SUBTAB_Y + 2, colour = TEXT_DARK, centered = true, scale = 0.5F)
        }

        val contentTop = SUBTAB_Y + SUBTAB_H + 1
        panel(context, left, y, contentTop)
        val bucketSpawns = spawns.filter { it.bucket == current }
        val totalWeight = bucketSpawns.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0
        val visible = (CONTENT_BOTTOM - contentTop) / ROW_TEXT_H
        clampScroll(bucketSpawns.size, visible)
        val end = (contentScroll + visible).coerceAtMost(bucketSpawns.size)
        for (i in contentScroll until end) {
            val spawn = bucketSpawns[i]
            val rowY = y + contentTop + 2 + (i - contentScroll) * ROW_TEXT_H
            drawScaledText(context = context, text = spawn.name, x = left + 4, y = rowY, shadow = true, scale = 0.6F)
            val lvl = if (spawn.levelRange.isNotEmpty()) " Lv.${spawn.levelRange}" else ""
            drawScaledText(context = context, text = Component.literal("%.0f%%%s".format(spawn.weight / totalWeight * 100, lvl)), x = left + DETAIL_W - 52, y = rowY, shadow = true, scale = 0.55F)
        }
    }

    private fun renderLoot(context: GuiGraphics, left: Int, y: Int, entry: WorldDexData.Entry, sus: Boolean) {
        val loot = WorldDexLoot.lootFor(entry)
        val items = if (sus) loot.susItems else loot.chestItems
        val contentTop = TABS_Y + TAB_H + 1
        panel(context, left, y, contentTop)
        if (items.isEmpty()) {
            centeredMessage(context, left, y, contentTop, if (sus) fallback("no_sus", "No suspicious finds") else fallback("no_loot", "No loot data"))
            return
        }
        val visible = (CONTENT_BOTTOM - contentTop) / ROW_TEXT_H
        clampScroll(items.size, visible)
        val end = (contentScroll + visible).coerceAtMost(items.size)
        for (i in contentScroll until end) {
            val itemId = items[i]
            val rowY = y + contentTop + 1 + (i - contentScroll) * ROW_TEXT_H
            val stack = ItemStack(BuiltInRegistries.ITEM.get(itemId))
            renderScaledGuiItemIcon(stack, (left + 4).toDouble(), rowY.toDouble(), matrixStack = context.pose(), scale = 0.5)
            drawScaledText(context = context, text = stack.hoverName.copy(), x = left + 14, y = rowY + 1, shadow = true, scale = 0.6F)
        }
    }

    private fun renderBiomes(context: GuiGraphics, left: Int, y: Int, entry: WorldDexData.Entry) {
        val tagBiomes = WorldDexBiomes.biomesFor(entry)
        val contentTop = TABS_Y + TAB_H + 1
        panel(context, left, y, contentTop)
        // Flatten into rows: a tag header followed by its biomes (indented).
        val rows = ArrayList<Pair<MutableComponent, Boolean>>() // text, isHeader
        for (tb in tagBiomes) {
            rows.add(tb.tagName.copy().bold() to true)
            if (tb.biomes.isEmpty()) rows.add(fallback("no_biomes", "(none loaded)") to false)
            else tb.biomes.forEach { rows.add(tb.biomeName(it) to false) }
        }
        if (rows.isEmpty()) {
            centeredMessage(context, left, y, contentTop, fallback("no_biomes_tags", "No biome data"))
            return
        }
        val visible = (CONTENT_BOTTOM - contentTop) / ROW_TEXT_H
        clampScroll(rows.size, visible)
        val end = (contentScroll + visible).coerceAtMost(rows.size)
        for (i in contentScroll until end) {
            val (text, header) = rows[i]
            val rowY = y + contentTop + 2 + (i - contentScroll) * ROW_TEXT_H
            drawScaledText(context = context, text = text, x = left + (if (header) 4 else 12), y = rowY, shadow = true, scale = 0.55F)
        }
    }

    private fun panel(context: GuiGraphics, left: Int, y: Int, contentTop: Int) {
        context.fill(left, y + contentTop, left + DETAIL_W, y + CONTENT_BOTTOM, PANEL_BG)
    }

    private fun centeredMessage(context: GuiGraphics, left: Int, y: Int, contentTop: Int, text: MutableComponent) {
        drawScaledText(context = context, text = text, x = left + DETAIL_W / 2, y = y + (contentTop + CONTENT_BOTTOM) / 2 - 3, centered = true, shadow = true, scale = 0.6F)
    }

    private fun clampScroll(count: Int, visible: Int) {
        contentScroll = contentScroll.coerceIn(0, max(0, count - visible))
    }

    private fun hasGimmighoulChest(variantId: ResourceLocation): Boolean = chestCache.getOrPut(variantId) {
        StructurePreviewLoader.forEntry(variantId)?.blocks?.any { it.state.block is GildedChestBlock } == true
    }

    private fun chestIconStack(): ItemStack {
        val stack = ItemStack(CobblemonBlocks.GIMMIGHOUL_CHEST)
        return if (stack.isEmpty) ItemStack(net.minecraft.world.item.Items.CHEST) else stack
    }

    // ---- input ----

    fun mouseClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        // Search focus.
        if (mouseX >= x + LIST_X && mouseX <= x + LIST_X + LIST_W && mouseY >= y + SEARCH_Y && mouseY <= y + SEARCH_Y + SEARCH_H) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // Left list.
        if (mouseX >= x + LIST_X && mouseX <= x + LIST_X + LIST_W) {
            val list = entries()
            val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(list.size)
            for (i in scrollRow until end) {
                val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
                if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2) {
                    selected = list[i]
                    selectedNumber = i + 1
                    variantIndex = 0
                    activeTab = tabsFor(category).first()
                    contentScroll = 0
                    spawnBucket = null
                    resetView()
                    return true
                }
            }
            return false
        }

        val entry = selected ?: return false
        val left = x + DETAIL_X

        // Variation arrows.
        val variants = entry.variants
        if (variants.size > 1) {
            val top = y + VIEW_Y + VIEW_H / 2 - 8
            if (mouseY >= top && mouseY <= top + 16) {
                if (mouseX >= left && mouseX <= left + ARROW_W) { variantIndex = (variantIndex - 1 + variants.size) % variants.size; return true }
                if (mouseX >= left + DETAIL_W - ARROW_W && mouseX <= left + DETAIL_W) { variantIndex = (variantIndex + 1) % variants.size; return true }
            }
        }

        // Category tabs.
        val tabs = tabsFor(category)
        if (mouseY >= y + TABS_Y && mouseY <= y + TABS_Y + TAB_H) {
            val tabW = (DETAIL_W - (tabs.size - 1) * 2) / tabs.size
            tabs.forEachIndexed { index, tab ->
                val tx = left + index * (tabW + 2)
                if (mouseX >= tx && mouseX <= tx + tabW) { activeTab = tab; contentScroll = 0; spawnBucket = null; return true }
            }
        }

        // Spawn rarity sub-buttons.
        if (activeTab == Tab.SPAWNS && mouseY >= y + SUBTAB_Y && mouseY <= y + SUBTAB_Y + SUBTAB_H) {
            val buckets = WorldDexHabitatSpawns.spawnsFor(entry).map { it.bucket }.distinct().sortedBy { WorldDexHabitatSpawns.bucketOrder(it) }
            if (buckets.isNotEmpty()) {
                val buttonW = (DETAIL_W - (buckets.size - 1) * 2) / buckets.size
                buckets.forEachIndexed { index, bucket ->
                    val tx = left + index * (buttonW + 2)
                    if (mouseX >= tx && mouseX <= tx + buttonW) { spawnBucket = bucket; contentScroll = 0; return true }
                }
            }
        }
        return false
    }

    fun mouseDragged(deltaX: Double, deltaY: Double): Boolean {
        if (selected == null) return false
        yaw += deltaX.toFloat()
        pitch = (pitch + deltaY.toFloat()).coerceIn(-90f, 90f)
        return true
    }

    fun mouseScrolled(x: Int, y: Int, mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        // Viewport zooms; right-side content scrolls; otherwise the left list scrolls.
        if (selected != null && mouseX >= x + DETAIL_X && mouseX <= x + DETAIL_X + DETAIL_W) {
            if (mouseY >= y + VIEW_Y && mouseY <= y + VIEW_Y + VIEW_H) {
                zoom = (zoom + deltaY.toFloat() * 0.1f).coerceIn(0.25f, 4f)
                return true
            }
            if (mouseY >= y + TABS_Y + TAB_H) {
                contentScroll = (contentScroll - deltaY.toInt()).coerceAtLeast(0)
                return true
            }
        }
        val maxScroll = max(0, entries().size - VISIBLE_ROWS)
        scrollRow = (scrollRow - deltaY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    fun charTyped(codePoint: Char): Boolean {
        if (!searchFocused) return false
        if (codePoint.code >= 32) { searchQuery += codePoint; scrollRow = 0 }
        return true
    }

    fun keyPressed(keyCode: Int): Boolean {
        if (!searchFocused) return false
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> if (searchQuery.isNotEmpty()) { searchQuery = searchQuery.dropLast(1); scrollRow = 0 }
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> searchFocused = false
        }
        return true
    }
}
