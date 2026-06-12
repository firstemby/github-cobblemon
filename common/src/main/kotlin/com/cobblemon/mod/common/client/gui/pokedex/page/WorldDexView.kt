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
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max

/**
 * The WorldDex: a two-pane browser replacing the CobblePedia Habitats/Structures menus. Left pane is
 * an expandable list of biome groups (icon + name + unlock symbol); expanding one reveals its
 * structures and placed features grouped by type. Selecting an entry shows the detail pane on the
 * right: numbered name header, "Variation N" label, the rotatable/zoomable structure preview with
 * left/right arrows cycling the .nbt variations, and tabs underneath for Pokémon spawns and loot.
 *
 * Placeholder flat-colour styling for now (mirrors the mockup's shapes); swapping in the real Pokédex
 * textures is a later art pass. The spawn/loot tab bodies are stubs pending data wiring.
 */
class WorldDexView {
    companion object {
        // Left list pane
        private const val LIST_X = 18
        private const val LIST_W = 142
        private const val LIST_TOP = 42
        private const val SEARCH_Y = 26
        private const val SEARCH_H = 12
        private const val ROW_H = 13
        private const val VISIBLE_ROWS = 10
        private const val INDENT = 12

        // Right detail pane
        private const val DETAIL_X = 166
        private const val DETAIL_W = 160
        private const val HEADER_Y = 26
        private const val HEADER_H = 12
        private const val VARIATION_Y = 41
        private const val VIEW_Y = 50
        private const val VIEW_H = 92
        private const val ARROW_W = 10
        private const val TABS_Y = 146
        private const val TAB_H = 12
        private const val CONTENT_Y = 161
        private const val CONTENT_H = 30
        private const val SPAWN_ROW_H = 9
        private const val SPAWN_VISIBLE_ROWS = 3

        // Placeholder palette approximating the mockup (real textures are a later pass).
        private const val PANEL_BG = 0xCC10303A.toInt()
        private const val ROW_PLATE = 0xFFE9F6F6.toInt()
        private const val ROW_PLATE_HOVER = 0xFFC8E8E8.toInt()
        private const val ROW_PLATE_SELECTED = 0xFFA9DCDC.toInt()
        private const val ICON_BG = 0xFF2E6F8C.toInt()
        private const val BORDER = 0xFF7EE7E5.toInt()
        private const val UNLOCK_DOT = 0xFF49C7C2.toInt()
        private const val TEXT_DARK = 0x33586B
        private const val PREVIEW_BG = 0xC0101010.toInt()

        private fun fallback(key: String, text: String) = Component.translatableWithFallback("cobblemon.ui.worlddex.$key", text)
    }

    private sealed interface Row
    private class GroupRow(val group: WorldDexData.BiomeGroup) : Row
    private class TypeRow(val label: Component) : Row
    private class EntryRow(val entry: WorldDexData.Entry, val number: Int, val group: WorldDexData.BiomeGroup) : Row

    private var scrollRow = 0
    private val expanded = LinkedHashSet<ResourceLocation>()
    private var selected: WorldDexData.Entry? = null
    private var selectedGroup: WorldDexData.BiomeGroup? = null
    private var selectedNumber = 0
    private var variantIndex = 0
    private var spawnScroll = 0
    private var activeBucket: String? = null
    private var searchQuery = ""
    private var searchFocused = false
    private var yaw = -30f
    private var pitch = 30f
    private var zoom = 1f

    /** variant id -> whether its (processor-applied) blocks include a Gimmighoul/gilded chest. */
    private val chestCache = HashMap<ResourceLocation, Boolean>()

    fun reset() {
        scrollRow = 0
        expanded.clear()
        selected = null
        selectedGroup = null
        selectedNumber = 0
        variantIndex = 0
        spawnScroll = 0
        activeBucket = null
        searchQuery = ""
        searchFocused = false
        resetView()
    }

    /**
     * Biome groups filtered by the search query: a group passes if its biome name matches, or is
     * narrowed to the entries whose name or Pokémon spawns match the query.
     */
    private fun filteredGroups(): List<WorldDexData.BiomeGroup> {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return WorldDexData.groups
        return WorldDexData.groups.mapNotNull { group ->
            if (group.name.string.lowercase().contains(query)) return@mapNotNull group
            val entries = group.entries.filter { entry ->
                entry.name.string.lowercase().contains(query) ||
                    WorldDexSpawnData.spawnsFor(group.tagId, entry.id).any {
                        it.species.contains(query) || it.name.string.lowercase().contains(query)
                    }
            }
            if (entries.isEmpty()) null else WorldDexData.BiomeGroup(group.tagId, entries)
        }
    }

    private fun resetView() {
        yaw = -30f
        pitch = 30f
        zoom = 1f
    }

    /** The flattened, currently-visible row list (group rows + the expanded groups' contents). */
    private fun rows(): List<Row> {
        val rows = ArrayList<Row>()
        val searching = searchQuery.isNotBlank()
        for (group in filteredGroups()) {
            rows.add(GroupRow(group))
            // While searching, matching groups auto-expand so the hits are visible.
            if (group.tagId !in expanded && !searching) continue
            var number = 0
            var lastType: WorldDexData.EntryType? = null
            for (entry in group.entries) {
                if (entry.type != lastType) {
                    rows.add(TypeRow(entry.type.label))
                    lastType = entry.type
                }
                rows.add(EntryRow(entry, ++number, group))
            }
        }
        return rows
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
        context.fill(left, top, left + LIST_W, top + SEARCH_H, if (searchFocused || hovered) ROW_PLATE_HOVER else ROW_PLATE)
        val text: MutableComponent = when {
            searchQuery.isEmpty() && !searchFocused -> fallback("search", "Search")
            else -> Component.literal(searchQuery + if (searchFocused) "_" else "")
        }
        drawScaledText(
            context = context,
            text = text,
            x = left + 3,
            y = top + 3,
            colour = TEXT_DARK,
            scale = 0.65F
        )
    }

    private fun renderList(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val rows = rows()
        val maxScroll = max(0, rows.size - VISIBLE_ROWS)
        if (scrollRow > maxScroll) scrollRow = maxScroll

        val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(rows.size)
        for (i in scrollRow until end) {
            val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
            when (val row = rows[i]) {
                is GroupRow -> renderGroupRow(context, x, rowY, mouseX, mouseY, row)
                is TypeRow -> drawScaledText(
                    context = context,
                    text = row.label.copy().bold(),
                    x = x + LIST_X + INDENT,
                    y = rowY + 3,
                    shadow = true,
                    scale = 0.5F
                )
                is EntryRow -> renderEntryRow(context, x, rowY, mouseX, mouseY, row)
            }
        }
    }

    private fun renderGroupRow(context: GuiGraphics, x: Int, rowY: Int, mouseX: Int, mouseY: Int, row: GroupRow) {
        val left = x + LIST_X
        val hovered = mouseX in left..(left + LIST_W) && mouseY in rowY..(rowY + ROW_H - 2)
        // Biome icon plate (blue square; real icons are a later art pass).
        context.fill(left, rowY, left + 11, rowY + 11, ICON_BG)
        context.fill(left, rowY, left + 11, rowY + 1, BORDER)
        // Name plate.
        val plateLeft = left + 13
        val plateRight = left + LIST_W - 12
        context.fill(plateLeft, rowY, plateRight, rowY + 11, if (hovered) ROW_PLATE_HOVER else ROW_PLATE)
        drawScaledText(
            context = context,
            text = row.group.name.copy().bold(),
            x = plateLeft + 3,
            y = rowY + 3,
            colour = TEXT_DARK,
            scale = 0.65F
        )
        // Expand marker + unlocked symbol (everything unlocked while testing).
        drawScaledText(
            context = context,
            text = Component.literal(if (row.group.tagId in expanded) "v" else ">"),
            x = plateRight - 6,
            y = rowY + 3,
            colour = TEXT_DARK,
            scale = 0.65F
        )
        context.fill(left + LIST_W - 9, rowY + 2, left + LIST_W - 2, rowY + 9, UNLOCK_DOT)
    }

    private fun renderEntryRow(context: GuiGraphics, x: Int, rowY: Int, mouseX: Int, mouseY: Int, row: EntryRow) {
        val left = x + LIST_X + INDENT
        val plateRight = x + LIST_X + LIST_W - 12
        val hovered = mouseX in left..(plateRight) && mouseY in rowY..(rowY + ROW_H - 2)
        val plate = when {
            row.entry === selected -> ROW_PLATE_SELECTED
            hovered -> ROW_PLATE_HOVER
            else -> ROW_PLATE
        }
        context.fill(left, rowY, plateRight, rowY + 11, plate)
        drawScaledText(
            context = context,
            text = Component.literal("%04d".format(row.number)),
            x = left + 2,
            y = rowY + 3,
            colour = TEXT_DARK,
            scale = 0.55F
        )
        drawScaledText(
            context = context,
            text = row.entry.name,
            x = left + 22,
            y = rowY + 3,
            colour = TEXT_DARK,
            scale = 0.65F
        )
        context.fill(x + LIST_X + LIST_W - 9, rowY + 2, x + LIST_X + LIST_W - 2, rowY + 9, UNLOCK_DOT)
    }

    private fun renderDetail(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val entry = selected ?: return
        val left = x + DETAIL_X

        // Header: number + name.
        context.fill(left, y + HEADER_Y, left + DETAIL_W, y + HEADER_Y + HEADER_H, ROW_PLATE)
        drawScaledText(
            context = context,
            text = Component.literal("%04d".format(selectedNumber)).bold(),
            x = left + 3,
            y = y + HEADER_Y + 3,
            colour = TEXT_DARK,
            scale = 0.65F
        )
        drawScaledText(
            context = context,
            text = entry.name.copy().bold(),
            x = left + 30,
            y = y + HEADER_Y + 3,
            colour = TEXT_DARK,
            scale = 0.75F
        )

        val variants = entry.variants
        if (variants.isEmpty()) {
            drawScaledText(
                context = context,
                text = fallback("no_preview", "No preview available"),
                x = left + DETAIL_W / 2,
                y = y + VIEW_Y + VIEW_H / 2,
                centered = true,
                shadow = true,
                scale = 0.65F
            )
            return
        }
        if (variantIndex >= variants.size) variantIndex = 0

        // Variation label above the viewport.
        if (variants.size > 1) {
            drawScaledText(
                context = context,
                text = Component.translatableWithFallback("cobblemon.ui.worlddex.variation", "Variation %s", variantIndex + 1),
                x = left + DETAIL_W / 2,
                y = y + VARIATION_Y,
                centered = true,
                shadow = true,
                scale = 0.65F
            )
        }

        // Viewport + cycle arrows.
        val viewLeft = left + ARROW_W + 2
        val viewWidth = DETAIL_W - (ARROW_W + 2) * 2
        context.fill(viewLeft, y + VIEW_Y, viewLeft + viewWidth, y + VIEW_Y + VIEW_H, PREVIEW_BG)
        val preview = StructurePreviewLoader.forEntry(variants[variantIndex])
        if (preview != null) {
            StructurePreviewRenderer.render(context, preview, viewLeft, y + VIEW_Y, viewWidth, VIEW_H, yaw, pitch, zoom)
        }
        if (variants.size > 1) {
            renderArrow(context, left, y, mouseX, mouseY, false)
            renderArrow(context, left + DETAIL_W - ARROW_W, y, mouseX, mouseY, true)
        }

        // Gimmighoul chest indicator: shown when this variation's blocks (after processor application,
        // which is what converts plain chests into gimmighoul/gilded chests) include one.
        if (hasGimmighoulChest(variants[variantIndex])) {
            renderScaledGuiItemIcon(
                itemStack = chestIconStack(),
                x = (left + DETAIL_W - 12).toDouble(),
                y = (y + HEADER_Y + 1).toDouble(),
                matrixStack = context.pose(),
                scale = 0.65
            )
        }

        // Pokémon spawns: one button per rarity bucket, then that bucket's spawns with their share of
        // the bucket's total weight, scrollable.
        val spawns = WorldDexSpawnData.spawnsFor(selectedGroup?.tagId, entry.id)
        context.fill(left, y + CONTENT_Y, left + DETAIL_W, y + CONTENT_Y + CONTENT_H, PANEL_BG)
        if (spawns.isEmpty()) {
            drawScaledText(
                context = context,
                text = fallback("no_spawns", "No known spawns"),
                x = left + DETAIL_W / 2,
                y = y + CONTENT_Y + CONTENT_H / 2 - 3,
                centered = true,
                shadow = true,
                scale = 0.6F
            )
            return
        }

        val buckets = spawnBuckets(spawns)
        val current = currentBucket(spawns)
        val buttonW = (DETAIL_W - (buckets.size - 1) * 2) / buckets.size
        buckets.forEachIndexed { index, bucket ->
            val label = Component.literal(spawns.first { it.bucket == bucket }.bucketLabel)
            renderTab(context, left + index * (buttonW + 2), y + TABS_Y, buttonW, label, bucket == current, mouseX, mouseY)
        }

        val bucketSpawns = spawns.filter { it.bucket == current }
        val totalWeight = bucketSpawns.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0
        val maxSpawnScroll = max(0, bucketSpawns.size - SPAWN_VISIBLE_ROWS)
        if (spawnScroll > maxSpawnScroll) spawnScroll = maxSpawnScroll
        val endSpawn = (spawnScroll + SPAWN_VISIBLE_ROWS).coerceAtMost(bucketSpawns.size)
        for (i in spawnScroll until endSpawn) {
            val spawn = bucketSpawns[i]
            val rowY = y + CONTENT_Y + 2 + (i - spawnScroll) * SPAWN_ROW_H
            drawScaledText(
                context = context,
                text = spawn.name,
                x = left + 4,
                y = rowY,
                shadow = true,
                scale = 0.6F
            )
            drawScaledText(
                context = context,
                text = Component.literal("%.1f%%".format(spawn.weight / totalWeight * 100)),
                x = left + DETAIL_W - 26,
                y = rowY,
                shadow = true,
                scale = 0.6F
            )
        }
    }

    /** Rarity buckets present in this entry's spawn list, common-first. */
    private fun spawnBuckets(spawns: List<WorldDexSpawnData.SpawnEntry>): List<String> =
        spawns.map { it.bucket }.distinct().sortedBy { WorldDexSpawnData.bucketOrder(it) }

    /** The active rarity bucket, defaulting to the first available one. */
    private fun currentBucket(spawns: List<WorldDexSpawnData.SpawnEntry>): String? {
        val buckets = spawnBuckets(spawns)
        return activeBucket?.takeIf { it in buckets } ?: buckets.firstOrNull()
    }

    /** Whether a variation's preview blocks contain any gilded/Gimmighoul chest, cached per variant. */
    private fun hasGimmighoulChest(variantId: ResourceLocation): Boolean = chestCache.getOrPut(variantId) {
        StructurePreviewLoader.forEntry(variantId)?.blocks?.any { it.state.block is GildedChestBlock } == true
    }

    private fun chestIconStack(): ItemStack {
        val stack = ItemStack(CobblemonBlocks.GIMMIGHOUL_CHEST)
        return if (stack.isEmpty) ItemStack(net.minecraft.world.item.Items.CHEST) else stack
    }

    private fun renderArrow(context: GuiGraphics, ax: Int, y: Int, mouseX: Int, mouseY: Int, right: Boolean) {
        val top = y + VIEW_Y + VIEW_H / 2 - 8
        val hovered = mouseX in ax..(ax + ARROW_W) && mouseY in top..(top + 16)
        context.fill(ax, top, ax + ARROW_W, top + 16, if (hovered) ROW_PLATE_HOVER else ROW_PLATE)
        drawScaledText(
            context = context,
            text = Component.literal(if (right) ">" else "<").bold(),
            x = ax + ARROW_W / 2,
            y = top + 5,
            colour = TEXT_DARK,
            centered = true
        )
    }

    private fun renderTab(context: GuiGraphics, tx: Int, ty: Int, width: Int, label: MutableComponent, active: Boolean, mouseX: Int, mouseY: Int) {
        val hovered = mouseX in tx..(tx + width) && mouseY in ty..(ty + TAB_H)
        context.fill(tx, ty, tx + width, ty + TAB_H, if (active) ROW_PLATE_SELECTED else if (hovered) ROW_PLATE_HOVER else ROW_PLATE)
        drawScaledText(
            context = context,
            text = label,
            x = tx + width / 2,
            y = ty + 3,
            colour = TEXT_DARK,
            centered = true,
            scale = 0.6F
        )
    }

    // ---- input ----

    fun mouseClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        // Search bar focus.
        if (mouseX >= x + LIST_X && mouseX <= x + LIST_X + LIST_W &&
            mouseY >= y + SEARCH_Y && mouseY <= y + SEARCH_Y + SEARCH_H
        ) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // Left list.
        if (mouseX >= x + LIST_X && mouseX <= x + LIST_X + LIST_W) {
            val rows = rows()
            val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(rows.size)
            for (i in scrollRow until end) {
                val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
                if (mouseY < rowY || mouseY > rowY + ROW_H - 2) continue
                when (val row = rows[i]) {
                    is GroupRow -> {
                        if (!expanded.remove(row.group.tagId)) expanded.add(row.group.tagId)
                        return true
                    }
                    is EntryRow -> {
                        selected = row.entry
                        selectedGroup = row.group
                        selectedNumber = row.number
                        variantIndex = 0
                        spawnScroll = 0
                        activeBucket = null
                        resetView()
                        return true
                    }
                    is TypeRow -> return true
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
                if (mouseX >= left && mouseX <= left + ARROW_W) {
                    variantIndex = (variantIndex - 1 + variants.size) % variants.size
                    return true
                }
                if (mouseX >= left + DETAIL_W - ARROW_W && mouseX <= left + DETAIL_W) {
                    variantIndex = (variantIndex + 1) % variants.size
                    return true
                }
            }
        }

        // Rarity bucket buttons.
        if (mouseY >= y + TABS_Y && mouseY <= y + TABS_Y + TAB_H) {
            val spawns = WorldDexSpawnData.spawnsFor(selectedGroup?.tagId, entry.id)
            val buckets = spawnBuckets(spawns)
            if (buckets.isNotEmpty()) {
                val buttonW = (DETAIL_W - (buckets.size - 1) * 2) / buckets.size
                buckets.forEachIndexed { index, bucket ->
                    val tx = left + index * (buttonW + 2)
                    if (mouseX >= tx && mouseX <= tx + buttonW) {
                        activeBucket = bucket
                        spawnScroll = 0
                        return true
                    }
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

    fun charTyped(codePoint: Char): Boolean {
        if (!searchFocused) return false
        if (codePoint.code >= 32) {
            searchQuery += codePoint
            scrollRow = 0
        }
        return true
    }

    fun keyPressed(keyCode: Int): Boolean {
        if (!searchFocused) return false
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> if (searchQuery.isNotEmpty()) {
                searchQuery = searchQuery.dropLast(1)
                scrollRow = 0
            }
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> searchFocused = false
        }
        return true // consume everything while typing so hotkeys don't close the screen
    }

    fun mouseScrolled(x: Int, y: Int, mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        // Over the viewport: zoom. Over the spawn list: scroll it. Anywhere else: scroll the biome list.
        if (selected != null && mouseX >= x + DETAIL_X && mouseX <= x + DETAIL_X + DETAIL_W) {
            if (mouseY >= y + VIEW_Y && mouseY <= y + VIEW_Y + VIEW_H) {
                zoom = (zoom + deltaY.toFloat() * 0.1f).coerceIn(0.25f, 4f)
                return true
            }
            if (mouseY >= y + CONTENT_Y && mouseY <= y + CONTENT_Y + CONTENT_H) {
                val spawns = WorldDexSpawnData.spawnsFor(selectedGroup?.tagId, selected!!.id)
                val current = currentBucket(spawns)
                val spawnCount = spawns.count { it.bucket == current }
                val maxSpawnScroll = max(0, spawnCount - SPAWN_VISIBLE_ROWS)
                spawnScroll = (spawnScroll - deltaY.toInt()).coerceIn(0, maxSpawnScroll)
                return true
            }
        }
        val maxScroll = max(0, rows().size - VISIBLE_ROWS)
        scrollRow = (scrollRow - deltaY.toInt()).coerceIn(0, maxScroll)
        return true
    }
}
