/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.api.berry.Berry
import com.cobblemon.mod.common.api.cooking.Flavour
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.berry.BerryTreePreviewRenderer
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.max

/**
 * The BerryDex: a scrollable, sortable tile grid of every berry on the left (undiscovered berries
 * render as dark silhouettes named "????"), and a detail pane on the right for the selected berry.
 * The detail pane shows a drag-rotatable berry tree preview with arrows cycling its growth ages, and
 * tabs underneath for Description, Flavor stats, Crossbreeding partners, Bait effects, and preferred
 * Location/mulch.
 *
 * A berry counts as discovered once it has been scanned into the BERRIES bucket of the player's
 * scanned data.
 */
class BerryDexView {
    companion object {
        private const val GRID_X = 18
        private const val GRID_TOP = 56
        private const val SEARCH_Y = 26
        private const val SEARCH_H = 12
        private const val COLS = 5             // match the main Pokédex grid (5 portrait-tile columns)
        private const val SLOT = 25            // pokedex_slot.png native tile size
        private const val COL_STEP = 27        // SLOT plus 2px gap
        private const val ROW_STEP = 34        // SLOT plus name label plus gap
        private const val TILE_HIT_H = 33      // clickable cell height (slot plus name)
        private const val VISIBLE_TILE_ROWS = 4
        private const val SCROLLBAR_W = 3
        private const val SORT_Y = 42
        private const val SORT_H = 11

        private const val DETAIL_X = 166
        private const val DETAIL_W = 160
        private const val HEADER_Y = 26
        private const val VIEW_Y = 41
        private const val VIEW_H = 90
        private const val ARROW_W = 10
        private const val CONTENT_Y = 134      // content panel sits directly below the viewport
        private const val CONTENT_H = 38       // ends just above the bottom tab row
        private const val TAB_ROW_Y = 178      // info-tab icon row at the bottom
        private const val CONTENT_ROW_H = 9
        private const val CONTENT_VISIBLE_ROWS = 4

        private const val TILE_BG = 0xFFE9F6F6.toInt()
        private const val TILE_BG_HOVER = 0xFFC8E8E8.toInt()
        private const val BUTTON_BG = 0xFFE9F6F6.toInt()
        private const val BUTTON_BG_ACTIVE = 0xFFA9DCDC.toInt()
        private const val TEXT_DARK = 0x33586B
        private const val INFO_TEXT = 0x606B6E   // matches the main Pokédex info-page text colour
        private const val SCROLL_TRACK = 0x6010303A
        private const val SCROLL_THUMB = 0xFF7EE7E5.toInt()

        /** The Pokédex's existing "?" slot icon, reused for undiscovered berries. */
        private val UNKNOWN_ICON = cobblemonResource("textures/gui/pokedex/pokedex_slot_unknown.png")

        // Berry tiles reuse the main Pokédex portrait tile button (pokedex_slot.png) plus slot_select highlight.
        private val SLOT_TEXTURE = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")
        private val SLOT_SELECT = cobblemonResource("textures/gui/pokedex/slot_select.png")
        // Custom right-panel background skin, shared with the WorldDex.
        private val INFO_BG = cobblemonResource("textures/gui/pokedex/cobblepedia_info_background.png")
        // Bottom-right info tabs reuse the Pokédex tab-row style with best-fit existing tab icons.
        private val TAB_SELECT_ARROW = cobblemonResource("textures/gui/pokedex/select_arrow.png")
        private val TAB_INFO = cobblemonResource("textures/gui/pokedex/tab_info.png")
        private val TAB_STATS = cobblemonResource("textures/gui/pokedex/tab_stats.png")
        private val TAB_ABILITIES = cobblemonResource("textures/gui/pokedex/tab_abilities.png")
        private val TAB_MOVES = cobblemonResource("textures/gui/pokedex/tab_moves.png")
        private val TAB_LOCATIONS = cobblemonResource("textures/gui/pokedex/tab_locations.png")
        private const val TAB_ICON_NATIVE = 16
        private const val TAB_ICON_SCALE = 0.5f
        private const val TAB_ICON_DISP = 8
        private const val TAB_ICON_GAP = 22
        private const val TAB_ICON_X0 = 2

        private fun fallback(key: String, text: String) = Component.translatableWithFallback("cobblemon.ui.berrydex.$key", text)

        private fun prettify(value: String): String =
            value.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private enum class SortMode(val label: String) { QUALITY("Quality"), COLOR("Color"), ALPHA("A-Z") }
    private enum class Tab(val key: String, val label: String) {
        DESCRIPTION("tab.description", "Info"),
        FLAVOR("tab.flavor", "Flavor"),
        CROSSBREED("tab.crossbreed", "Cross"),
        BAIT("tab.bait", "Bait"),
        LOCATION("tab.location", "Where")
    }

    private var sortMode = SortMode.QUALITY
    private var ascending = true
    private var scrollRow = 0
    private var searchQuery = ""
    private var searchFocused = false
    private var selected: Berry? = null
    private var age = BerryBlock.FRUIT_AGE
    private var activeTab = Tab.DESCRIPTION
    private var contentScroll = 0
    private var yaw = -30f
    private var pitch = 20f
    private var zoom = 1f

    fun reset() {
        sortMode = SortMode.QUALITY
        ascending = true
        scrollRow = 0
        searchQuery = ""
        searchFocused = false
        selected = null
        age = BerryBlock.FRUIT_AGE
        activeTab = Tab.DESCRIPTION
        contentScroll = 0
        resetView()
    }

    private fun resetView() {
        yaw = -30f
        pitch = 20f
        zoom = 1f
    }

    // ---- data ----

    private fun berries(): List<Berry> {
        val filtered = Berries.all().filter { matchesSearch(it) }
        val sorted = when (sortMode) {
            // Highest quality first; berries without a datapack-defined quality (0) end up last, name-ordered.
            SortMode.QUALITY -> filtered.sortedWith(compareByDescending<Berry> { it.quality }.thenBy { it.identifier.path })
            SortMode.COLOR -> filtered.sortedWith(compareBy({ it.colour.id }, { it.identifier.path }))
            SortMode.ALPHA -> filtered.sortedBy { displayName(it) }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    /**
     * Keyword search across: berry name, colour, DOMINANT flavours (the berry's highest non-zero
     * flavour values, so "spicy" finds the spicy-leaning berries), bait effect type/subcategory, and
     * preferred biomes.
     */
    private fun matchesSearch(berry: Berry): Boolean {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return true
        if (displayName(berry).lowercase().contains(query)) return true
        if (berry.colour.name.lowercase().contains(query)) return true
        val maxFlavour = berry.flavours.values.maxOrNull() ?: 0
        if (maxFlavour > 0 && berry.flavours.any { (flavour, value) ->
                value == maxFlavour && flavour.name.lowercase().contains(query)
            }
        ) return true
        berry.item()?.let { item ->
            if (SpawnBaitEffects.getEffectsFromItemStack(ItemStack(item)).any { effect ->
                    effect.type.path.lowercase().contains(query) ||
                        effect.subcategory?.path?.lowercase()?.contains(query) == true
                }
            ) return true
        }
        if (berry.preferredBiomeTags.any { it.location.path.lowercase().contains(query) }) return true
        return false
    }

    private fun discovered(berry: Berry): Boolean =
        CobblemonClient.clientScannedData.has(ScannedDataCategory.BERRIES.bucket(), berry.identifier)

    /** "Cheri Berry" -> "Cheri". */
    private fun displayName(berry: Berry): String {
        val item = berry.item() ?: return prettify(berry.identifier.path)
        return ItemStack(item).hoverName.string.removeSuffix(" Berry").removeSuffix(" berry")
    }

    // ---- rendering ----

    fun render(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        renderSearchBar(context, x, y, mouseX, mouseY)
        renderSortButtons(context, x, y, mouseX, mouseY)
        renderGrid(context, x, y, mouseX, mouseY)
        renderDetail(context, x, y, mouseX, mouseY)
        renderBerryCount(context, x, y)
    }

    /** Discovered / total berries, shown top-right like the main Pokédex's caught/total counter. */
    private fun renderBerryCount(context: GuiGraphics, x: Int, y: Int) {
        val all = Berries.all()
        val text = Component.literal("${all.count { discovered(it) }} / ${all.count()}").bold()
        drawScaledTextJustifiedRight(context = context, font = CobblemonResources.DEFAULT_LARGE, text = text, x = x + DETAIL_X + DETAIL_W - 4, y = y + 14, colour = INFO_TEXT)
    }

    private fun searchBarWidth(): Int = COLS * COL_STEP - (COL_STEP - SLOT)

    private fun tabIcon(tab: Tab) = when (tab) {
        Tab.DESCRIPTION -> TAB_INFO
        Tab.FLAVOR -> TAB_STATS
        Tab.CROSSBREED -> TAB_ABILITIES
        Tab.BAIT -> TAB_MOVES
        Tab.LOCATION -> TAB_LOCATIONS
    }

    /** Screen X of bottom tab [index]'s 8x8 icon within the detail panel anchored at [left]. */
    private fun tabIconX(left: Int, index: Int) = left + TAB_ICON_X0 + index * TAB_ICON_GAP

    private fun renderSearchBar(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val left = x + GRID_X
        val top = y + SEARCH_Y
        val width = searchBarWidth()
        val hovered = mouseX in left..(left + width) && mouseY in top..(top + SEARCH_H)
        context.fill(left, top, left + width, top + SEARCH_H, if (searchFocused || hovered) TILE_BG_HOVER else TILE_BG)
        val text = when {
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

    private fun sortButtonRects(x: Int, y: Int): List<Triple<Int, Int, Int>> {
        // (left, top, width) for: QUALITY, COLOR, ALPHA, asc/desc toggle
        val widths = listOf(32, 30, 26, 14)
        var left = x + GRID_X
        return widths.map { w -> Triple(left, y + SORT_Y, w).also { left += w + 3 } }
    }

    private fun renderSortButtons(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val rects = sortButtonRects(x, y)
        val labels = listOf(SortMode.QUALITY.label, SortMode.COLOR.label, SortMode.ALPHA.label, if (ascending) "^" else "v")
        for ((index, rect) in rects.withIndex()) {
            val (bx, by, bw) = rect
            val active = index < 3 && SortMode.entries[index] == sortMode
            val hovered = mouseX in bx..(bx + bw) && mouseY in by..(by + SORT_H)
            context.fill(bx, by, bx + bw, by + SORT_H, if (active) BUTTON_BG_ACTIVE else if (hovered) TILE_BG_HOVER else BUTTON_BG)
            drawScaledText(
                context = context,
                text = Component.literal(labels[index]),
                x = bx + bw / 2,
                y = by + 3,
                colour = TEXT_DARK,
                centered = true,
                scale = 0.55F
            )
        }
    }

    private fun renderGrid(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val list = berries()
        val totalRows = ceil(list.size / COLS.toFloat()).toInt()
        val maxScroll = max(0, totalRows - VISIBLE_TILE_ROWS)
        if (scrollRow > maxScroll) scrollRow = maxScroll

        val startIndex = scrollRow * COLS
        val endIndex = (startIndex + VISIBLE_TILE_ROWS * COLS).coerceAtMost(list.size)
        for (i in startIndex until endIndex) {
            val berry = list[i]
            val col = (i - startIndex) % COLS
            val row = (i - startIndex) / COLS
            val tileX = x + GRID_X + col * COL_STEP
            val tileY = y + GRID_TOP + row * ROW_STEP
            val isDiscovered = discovered(berry)
            val hovered = mouseX in tileX..(tileX + SLOT) && mouseY in tileY..(tileY + TILE_HIT_H)
            // Portrait tile button, with the slot_select highlight (bottom half of the texture) on hover or selection.
            blitk(matrixStack = context.pose(), texture = SLOT_TEXTURE, x = tileX, y = tileY, width = SLOT, height = SLOT)
            if (berry === selected || hovered) {
                blitk(matrixStack = context.pose(), texture = SLOT_SELECT, x = tileX, y = tileY, width = SLOT, height = SLOT, vOffset = SLOT, textureHeight = SLOT * 2)
            }

            // Item sprite for discovered berries, the Pokédex "?" slot icon for undiscovered ones.
            if (isDiscovered) {
                berry.item()?.let { item ->
                    renderScaledGuiItemIcon(
                        itemStack = ItemStack(item),
                        x = (tileX + (SLOT - 16) / 2).toDouble(),
                        y = (tileY + 4).toDouble(),
                        matrixStack = context.pose(),
                        scale = 1.0
                    )
                }
            } else {
                blitk(
                    matrixStack = context.pose(),
                    texture = UNKNOWN_ICON,
                    x = tileX + (SLOT - 8) / 2,
                    y = tileY + 7,
                    width = 8,
                    height = 10
                )
            }

            drawScaledText(
                context = context,
                text = Component.literal(if (isDiscovered) displayName(berry) else "????"),
                x = tileX + SLOT / 2,
                y = tileY + SLOT + 1,
                colour = TEXT_DARK,
                centered = true,
                scale = 0.5F
            )
        }

        if (totalRows > VISIBLE_TILE_ROWS) {
            val trackX = x + GRID_X + COLS * COL_STEP
            val trackTop = y + GRID_TOP
            val trackH = VISIBLE_TILE_ROWS * ROW_STEP - (ROW_STEP - SLOT)
            context.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, SCROLL_TRACK)
            val thumbH = max(8, trackH * VISIBLE_TILE_ROWS / totalRows)
            val thumbY = trackTop + (trackH - thumbH) * scrollRow / max(1, maxScroll)
            context.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, SCROLL_THUMB)
        }
    }

    private fun renderDetail(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val berry = selected ?: return
        val left = x + DETAIL_X

        // Background skin first, behind all content.
        blitk(matrixStack = context.pose(), texture = INFO_BG, x = left, y = y + HEADER_Y, width = DETAIL_W, height = TAB_ROW_Y + TAB_ICON_DISP + 2 - HEADER_Y)
        drawScaledText(
            context = context,
            text = Component.literal(displayName(berry)).bold(),
            x = left + 4,
            y = y + HEADER_Y + 3,
            colour = TEXT_DARK,
            scale = 0.75F
        )

        val viewLeft = left + ARROW_W + 2
        val viewWidth = DETAIL_W - (ARROW_W + 2) * 2
        // The tree renders directly on the panel background texture for a cutout look (no dark backdrop).
        BerryTreePreviewRenderer.render(context, berry, age, viewLeft, y + VIEW_Y, viewWidth, VIEW_H, yaw, pitch, zoom)
        renderArrow(context, left, y, mouseX, mouseY, right = false)
        renderArrow(context, left + DETAIL_W - ARROW_W, y, mouseX, mouseY, right = true)
        drawScaledText(
            context = context,
            text = Component.translatableWithFallback("cobblemon.ui.berrydex.age", "Age %s", age),
            x = left + DETAIL_W / 2,
            y = y + VIEW_Y + VIEW_H - 8,
            centered = true,
            colour = INFO_TEXT,
            scale = 0.5F
        )

        // Info tabs: 16x32 two-state icons at half scale (bottom half on hover or active), select arrow over the active tab.
        Tab.entries.forEachIndexed { index, tab ->
            val ix = tabIconX(left, index)
            val active = tab == activeTab
            val hovered = mouseX in ix..(ix + TAB_ICON_DISP) && mouseY in (y + TAB_ROW_Y)..(y + TAB_ROW_Y + TAB_ICON_DISP)
            blitk(
                matrixStack = context.pose(),
                texture = tabIcon(tab),
                x = ix / TAB_ICON_SCALE,
                y = (y + TAB_ROW_Y) / TAB_ICON_SCALE,
                width = TAB_ICON_NATIVE,
                height = TAB_ICON_NATIVE,
                vOffset = if (hovered || active) TAB_ICON_NATIVE else 0,
                textureHeight = TAB_ICON_NATIVE * 2,
                scale = TAB_ICON_SCALE
            )
            if (active) {
                blitk(
                    matrixStack = context.pose(),
                    texture = TAB_SELECT_ARROW,
                    x = (ix + 1) / TAB_ICON_SCALE,
                    y = (y + TAB_ROW_Y - 4) / TAB_ICON_SCALE,
                    width = 12,
                    height = 6,
                    scale = TAB_ICON_SCALE
                )
            }
        }

        when (activeTab) {
            Tab.FLAVOR -> renderFlavorContent(context, left, y, berry)
            Tab.CROSSBREED -> renderCrossbreedContent(context, left, y, berry)
            else -> renderTextContent(context, left, y, textLines(berry))
        }
    }

    private fun renderArrow(context: GuiGraphics, ax: Int, y: Int, mouseX: Int, mouseY: Int, right: Boolean) {
        val top = y + VIEW_Y + VIEW_H / 2 - 8
        val hovered = mouseX in ax..(ax + ARROW_W) && mouseY in top..(top + 16)
        context.fill(ax, top, ax + ARROW_W, top + 16, if (hovered) TILE_BG_HOVER else TILE_BG)
        drawScaledText(
            context = context,
            text = Component.literal(if (right) ">" else "<").bold(),
            x = ax + ARROW_W / 2,
            y = top + 5,
            colour = TEXT_DARK,
            centered = true
        )
    }

    private fun renderFlavorContent(context: GuiGraphics, left: Int, y: Int, berry: Berry) {
        val flavours = Flavour.entries
        val end = (contentScroll + CONTENT_VISIBLE_ROWS).coerceAtMost(flavours.size)
        for (i in contentScroll until end) {
            val flavour = flavours[i]
            val value = berry.flavours[flavour] ?: 0
            val rowY = y + CONTENT_Y + 2 + (i - contentScroll) * CONTENT_ROW_H
            drawScaledText(
                context = context,
                text = Component.literal(prettify(flavour.name.lowercase())),
                x = left + 4,
                y = rowY,
                colour = INFO_TEXT,
                scale = 0.5F
            )
            drawScaledText(
                context = context,
                text = Component.literal(value.toString()),
                x = left + 44,
                y = rowY,
                colour = INFO_TEXT,
                scale = 0.5F
            )
            val barWidth = (value * 3).coerceAtMost(95)
            if (barWidth > 0) {
                context.fill(left + 58, rowY, left + 58 + barWidth, rowY + 6, 0xFF000000.toInt() or flavour.colour)
            }
        }
    }

    private fun renderCrossbreedContent(context: GuiGraphics, left: Int, y: Int, berry: Berry) {
        val mutations = berry.mutations.entries.toList()
        if (mutations.isEmpty()) {
            drawScaledText(
                context = context,
                text = fallback("no_crossbreeds", "No known crossbreeds"),
                x = left + DETAIL_W / 2,
                y = y + CONTENT_Y + CONTENT_H / 2 - 3,
                centered = true,
                colour = INFO_TEXT,
                scale = 0.55F
            )
            return
        }
        val end = (contentScroll + CONTENT_VISIBLE_ROWS).coerceAtMost(mutations.size)
        for (i in contentScroll until end) {
            val (partnerId, resultId) = mutations[i]
            val rowY = y + CONTENT_Y + 1 + (i - contentScroll) * CONTENT_ROW_H
            // Row layout: selected berry icon and name, plus partner, arrow, result.
            berry.item()?.let {
                renderScaledGuiItemIcon(ItemStack(it), (left + 4).toDouble(), rowY.toDouble(), matrixStack = context.pose(), scale = 0.5)
            }
            drawScaledText(
                context = context,
                text = Component.literal(displayName(berry)),
                x = left + 13,
                y = rowY + 1,
                colour = INFO_TEXT,
                scale = 0.5F
            )
            drawScaledText(context = context, text = Component.literal("+"), x = left + 47, y = rowY + 1, colour = INFO_TEXT, scale = 0.5F)
            renderCrossbreedBerry(context, Berries.getByIdentifier(partnerId), left + 53, left + 62, rowY)
            drawScaledText(context = context, text = Component.literal("->"), x = left + 97, y = rowY + 1, colour = INFO_TEXT, scale = 0.5F)
            renderCrossbreedBerry(context, Berries.getByIdentifier(resultId), left + 105, left + 114, rowY)
        }
    }

    /** One berry in a crossbreed row: sprite + short name, or the "?" icon and "????" until discovered. */
    private fun renderCrossbreedBerry(context: GuiGraphics, berry: Berry?, iconX: Int, nameX: Int, rowY: Int) {
        if (berry == null) return
        val isDiscovered = discovered(berry)
        if (isDiscovered) {
            berry.item()?.let { item ->
                renderScaledGuiItemIcon(ItemStack(item), iconX.toDouble(), rowY.toDouble(), matrixStack = context.pose(), scale = 0.5)
            }
        } else {
            blitk(matrixStack = context.pose(), texture = UNKNOWN_ICON, x = iconX + 1, y = rowY, width = 6, height = 8)
        }
        drawScaledText(
            context = context,
            text = Component.literal(if (isDiscovered) displayName(berry) else "????"),
            x = nameX,
            y = rowY + 1,
            colour = INFO_TEXT,
            scale = 0.5F
        )
    }

    private fun renderTextContent(context: GuiGraphics, left: Int, y: Int, lines: List<MutableComponent>) {
        if (lines.isEmpty()) {
            drawScaledText(
                context = context,
                text = fallback("no_data", "No data"),
                x = left + DETAIL_W / 2,
                y = y + CONTENT_Y + CONTENT_H / 2 - 3,
                centered = true,
                colour = INFO_TEXT,
                scale = 0.55F
            )
            return
        }
        val end = (contentScroll + CONTENT_VISIBLE_ROWS).coerceAtMost(lines.size)
        for (i in contentScroll until end) {
            drawScaledText(
                context = context,
                text = lines[i],
                x = left + 4,
                y = y + CONTENT_Y + 2 + (i - contentScroll) * CONTENT_ROW_H,
                colour = INFO_TEXT,
                scale = 0.5F
            )
        }
    }

    /** Content lines for the text-based tabs (Description, Bait, Location). */
    private fun textLines(berry: Berry): List<MutableComponent> = when (activeTab) {
        Tab.DESCRIPTION -> listOf(
            Component.translatableWithFallback(
                "cobblemon.ui.berrydex.description.${berry.identifier.path}",
                "No description available."
            )
        )
        Tab.BAIT -> {
            val item = berry.item()
            val effects = if (item == null) emptyList() else SpawnBaitEffects.getEffectsFromItemStack(ItemStack(item))
            effects.map { effect ->
                val name = prettify(effect.type.path)
                val sub = effect.subcategory?.let { " (${prettify(it.path)})" } ?: ""
                Component.literal("$name$sub: ${(effect.chance * 100).toInt()}%, x${effect.value}")
            }
        }
        Tab.LOCATION -> {
            val lines = mutableListOf<MutableComponent>()
            if (berry.favoriteMulches.isNotEmpty()) {
                lines.add(
                    fallback("mulch", "Mulch: ").append(
                        Component.literal(berry.favoriteMulches.joinToString(", ") { prettify(it.name.lowercase()) })
                    )
                )
            }
            berry.preferredBiomeTags.forEach { tag ->
                lines.add(Component.literal(prettify(tag.location.path.substringAfterLast('/').removePrefix("is_"))))
            }
            lines
        }
        else -> emptyList()
    }

    // ---- input ----

    fun mouseClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        // Search bar focus.
        if (mouseX >= x + GRID_X && mouseX <= x + GRID_X + searchBarWidth() &&
            mouseY >= y + SEARCH_Y && mouseY <= y + SEARCH_Y + SEARCH_H
        ) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // Sort buttons.
        sortButtonRects(x, y).forEachIndexed { index, (bx, by, bw) ->
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + SORT_H) {
                if (index < 3) sortMode = SortMode.entries[index] else ascending = !ascending
                scrollRow = 0
                return true
            }
        }

        // Grid tiles (only discovered berries are selectable).
        val list = berries()
        val startIndex = scrollRow * COLS
        val endIndex = (startIndex + VISIBLE_TILE_ROWS * COLS).coerceAtMost(list.size)
        for (i in startIndex until endIndex) {
            val col = (i - startIndex) % COLS
            val row = (i - startIndex) / COLS
            val tileX = x + GRID_X + col * COL_STEP
            val tileY = y + GRID_TOP + row * ROW_STEP
            if (mouseX >= tileX && mouseX <= tileX + SLOT && mouseY >= tileY && mouseY <= tileY + TILE_HIT_H) {
                if (discovered(list[i])) {
                    selected = list[i]
                    age = BerryBlock.FRUIT_AGE
                    activeTab = Tab.DESCRIPTION
                    contentScroll = 0
                    resetView()
                }
                return true
            }
        }

        val berry = selected ?: return false
        val left = x + DETAIL_X

        // Age arrows.
        val arrowTop = y + VIEW_Y + VIEW_H / 2 - 8
        if (mouseY >= arrowTop && mouseY <= arrowTop + 16) {
            if (mouseX >= left && mouseX <= left + ARROW_W) {
                age = (age - 1 + (BerryBlock.FRUIT_AGE + 1)) % (BerryBlock.FRUIT_AGE + 1)
                return true
            }
            if (mouseX >= left + DETAIL_W - ARROW_W && mouseX <= left + DETAIL_W) {
                age = (age + 1) % (BerryBlock.FRUIT_AGE + 1)
                return true
            }
        }

        // Tabs (bottom icon row).
        if (mouseY >= y + TAB_ROW_Y && mouseY <= y + TAB_ROW_Y + TAB_ICON_DISP) {
            Tab.entries.forEachIndexed { index, tab ->
                val ix = tabIconX(left, index)
                if (mouseX >= ix && mouseX <= ix + TAB_ICON_DISP) {
                    activeTab = tab
                    contentScroll = 0
                    return true
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
        if (selected != null && mouseX >= x + DETAIL_X && mouseX <= x + DETAIL_X + DETAIL_W) {
            if (mouseY >= y + VIEW_Y && mouseY <= y + VIEW_Y + VIEW_H) {
                zoom = (zoom + deltaY.toFloat() * 0.1f).coerceIn(0.25f, 4f)
                return true
            }
            if (mouseY >= y + CONTENT_Y && mouseY <= y + CONTENT_Y + CONTENT_H) {
                val rowCount = when (activeTab) {
                    Tab.FLAVOR -> Flavour.entries.size
                    Tab.CROSSBREED -> selected!!.mutations.size
                    else -> textLines(selected!!).size
                }
                val maxContentScroll = max(0, rowCount - CONTENT_VISIBLE_ROWS)
                contentScroll = (contentScroll - deltaY.toInt()).coerceIn(0, maxContentScroll)
                return true
            }
        }
        val totalRows = ceil(berries().size / COLS.toFloat()).toInt()
        val maxScroll = max(0, totalRows - VISIBLE_TILE_ROWS)
        scrollRow = (scrollRow - deltaY.toInt()).coerceIn(0, maxScroll)
        return true
    }
}
