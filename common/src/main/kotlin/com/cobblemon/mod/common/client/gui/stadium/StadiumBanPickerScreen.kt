/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.ProfileTransformType
import com.cobblemon.mod.common.client.gui.stadium.StadiumBanCatalog.Category
import com.cobblemon.mod.common.client.gui.stadium.StadiumBanCatalog.Entry
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumButton
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumCheckbox
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumDropdown
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumEditBox
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.cobblemon.mod.common.util.math.toRGB
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

class StadiumBanPickerScreen(
    private val parent: StadiumConfigScreen,
    config: StadiumFormatConfig,
    private var category: Category,
    heldItems: Map<ResourceLocation, String>,
    private val apply: (StadiumFormatConfig) -> Unit
) : Screen(Component.translatable("cobblemon.stadium.picker.stadium_restrictions")) {
    private val draft = StadiumConfiguration.copy(config)
    private val catalogs = Category.entries.associateWith { StadiumBanCatalog.load(it, heldItems) }.mapValues { (category, entries) ->
        entries + (category.entries(draft) - entries.map { it.id }.toSet()).map {
            Entry(it, Component.literal(it), Component.translatable("cobblemon.stadium.picker.saved_restriction_not_present_in_the"))
        }
    }
    private var geometry = StadiumScreenLayout.fit(320, 240)
    private var query = ""
    private var selectedOnly = false
    private var group = ""
    private var offset = 0
    private var selected: Entry? = null
    private var openDropdown: StadiumDropdown<*>? = null
    private var status = ""
    private val states = mutableMapOf<String, FloatingState>()
    private var rotation = 35f
    private var detailScroll = 0
    private var listWidgets = mutableListOf<StadiumButton>()
    private var filtered = emptyList<Entry>()
    private val layout get() = StadiumPickerLayout(geometry, StadiumPickerLayout.Kind.valueOf(category.name))
    private val left get() = layout.left
    private val right get() = layout.right
    private val top get() = layout.top
    private val bottom get() = layout.bottom
    private val listWidth get() = layout.listWidth
    private val detailLeft get() = layout.detailLeft
    private val detailWidth get() = layout.detailWidth
    private val rowHeight get() = layout.rowHeight
    private val visibleRows get() = layout.visibleRows
    private val modelHeight get() = ((bottom - top - 32) * 2 / 3).coerceIn(24, 110)
    private val slotTexture = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")

    override fun init() {
        openDropdown?.close()
        openDropdown = null
        geometry = StadiumScreenLayout.fit(width, height)
        clearWidgets()
        listWidgets.clear()
        val tabWidth = (right - left) / 4
        Category.entries.forEachIndexed { index, value ->
            addRenderableWidget(StadiumButton(left + index * tabWidth, geometry.y + 34, tabWidth - 2, 20,
                Component.literal(value.label), category == value) {
                category = value; query = ""; group = ""; offset = 0; selected = null; detailScroll = 0; init()
            })
        }
        val groups = listOf("") + catalogs.getValue(category).flatMap { it.groups }.distinct().sorted()
        val filterWidth = (right - left) / 4
        val searchWidth = right - left - filterWidth * (if (groups.size > 1) 2 else 1) - 4
        val search = StadiumEditBox(font, left, geometry.y + 59, searchWidth, Component.literal(I18n.get("cobblemon.stadium.picker.search_2", category.label)))
        search.setHint(Component.literal(I18n.get("cobblemon.stadium.picker.search", category.label)))
        search.setTextColor(StadiumSkin.INK)
        search.value = query
        search.setResponder { query = it; offset = 0; rebuildEntries() }
        addRenderableWidget(search)
        if (groups.size > 1) {
            addRenderableWidget(StadiumDropdown(left + searchWidth + 4, geometry.y + 59, filterWidth - 4, 22, I18n.get("cobblemon.stadium.picker.type"), groups, group, { it.ifEmpty { I18n.get("cobblemon.stadium.picker.all_types") } },
                { openDropdown?.close(); openDropdown = it }) { group = it; offset = 0; rebuildEntries() })
        }
        addRenderableWidget(StadiumCheckbox(right - filterWidth, geometry.y + 59, filterWidth, I18n.get("cobblemon.stadium.picker.banned_2"), selectedOnly) {
            selectedOnly = it; offset = 0; rebuildEntries()
        })
        addRenderableWidget(StadiumButton(right - 65, geometry.y + geometry.height - 32, 65, 20, Component.translatable("cobblemon.stadium.picker.apply"), true) {
            apply(draft); minecraft?.setScreen(parent)
        })
        addRenderableWidget(StadiumButton(right - 128, geometry.y + geometry.height - 32, 59, 20, Component.translatable("cobblemon.stadium.picker.cancel")) { onClose() })
        addRenderableWidget(StadiumButton(left, geometry.y + geometry.height - 32, 56, 20, Component.translatable("cobblemon.stadium.picker.clear")) {
            category.entries(draft).clear(); rebuildEntries()
        })
        rebuildEntries()
    }

    private fun rebuildEntries() {
        val focusIndex = listWidgets.indexOfFirst { it.isFocused }
        listWidgets.forEach { removeWidget(it) }
        listWidgets.clear()
        val entries = category.entries(draft)
        filtered = catalogs.getValue(category).filter {
            (query.isBlank() || it.name.string.contains(query, true) || it.id.contains(query, true)) &&
                (!selectedOnly || it.id in entries) && (group == "" || group in it.groups)
        }
        offset = offset.coerceIn(0, layout.maxOffset(filtered.size))
        if (selected !in filtered) { selected = filtered.getOrNull(offset * layout.columns); detailScroll = 0 }
        filtered.drop(offset * layout.columns).take(layout.capacity).forEachIndexed { index, entry ->
            val tile = object : StadiumButton(left + index % layout.columns * layout.cellWidth,
                top + index / layout.columns * rowHeight, layout.cellWidth - 3, rowHeight - 3,
                entry.name.copy().append(if (entry.id in entries) I18n.get("cobblemon.stadium.picker.banned_activate_to_allow") else I18n.get("cobblemon.stadium.picker.allowed_activate_to_ban")),
                onPress = {
                    selected = entry
                    toggle(entry)
                    rebuildEntries()
                    listWidgets.getOrNull(index.coerceAtMost(listWidgets.lastIndex))?.let { focused = it }
                }) {
                override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                    renderCard(graphics, entry, this, partialTick)
                }
            }
            listWidgets.add(tile)
            addRenderableWidget(tile)
        }
        if (focusIndex >= 0) focused = listWidgets.getOrNull(focusIndex.coerceAtMost(listWidgets.lastIndex))
    }

    private fun toggle(entry: Entry) {
        val entries = category.entries(draft)
        if (entry.id in entries) { entries.remove(entry.id); status = "" }
        else if (entries.size < 256) { entries.add(entry.id); status = "" }
        else status = I18n.get("cobblemon.stadium.picker.limit_256_per_category")
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xB0101D28.toInt())
        StadiumSkin.frame(graphics, geometry.x, geometry.y, geometry.width, geometry.height)
        graphics.drawString(font, font.plainSubstrByWidth("${draft.displayName.string} / ${category.label}", right - left), left, geometry.y + 17, StadiumSkin.INK, false)
        StadiumSkin.panel(graphics, StadiumSkin.Surface.DISPLAY, left, top, listWidth, bottom - top)
        val hoveredIndex = if (openDropdown?.isOpen == true) -1 else listWidgets.indexOfFirst { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
        val focusIndex = listWidgets.indexOfFirst { it.isFocused }
        val previewIndex = if (hoveredIndex >= 0) hoveredIndex else focusIndex
        if (previewIndex >= 0) filtered.getOrNull(layout.entryIndex(offset, previewIndex))?.takeIf { it != selected }?.let {
            selected = it; detailScroll = 0; rotation = 35f
        }
        if (layout.hasDetail) {
            StadiumSkin.panel(graphics, StadiumSkin.Surface.DISPLAY, detailLeft, top, detailWidth, bottom - top)
            graphics.enableScissor(detailLeft + 2, top + 2, right - 2, bottom - 14)
            try {
                selected?.let { entry ->
                var y = top + 5 - detailScroll
                font.split(entry.name, detailWidth - 12).forEach { line ->
                    graphics.drawString(font, line, detailLeft + 6, y, StadiumSkin.INK, false); y += 10
                }
                if (entry.species != null) {
                    entry.species.types.forEachIndexed { index, type -> TypeIcon(detailLeft + 6 + index * 18, y, type, small = true).render(graphics) }
                    y += 12
                    graphics.enableScissor(detailLeft, y, right, y + modelHeight)
                    try {
                        drawPokemon(graphics, entry, detailLeft + detailWidth / 2, y - (modelHeight * 12f / 68f).toInt(), modelHeight * 40f / 68f, rotation, partialTick)
                    } finally {
                        graphics.disableScissor()
                    }
                    y += modelHeight
                } else if (!entry.item.isEmpty) {
                    graphics.renderItem(entry.item, detailLeft + 6, y + 2)
                    y += 23
                }
                font.split(entry.detail, detailWidth - 12).forEach { line ->
                    if (y >= top && y + 9 <= bottom - 14) graphics.drawString(font, line, detailLeft + 6, y, StadiumSkin.MUTED, false)
                    y += 10
                }
                }
            } finally {
                graphics.disableScissor()
            }
            val banned = selected?.id in category.entries(draft)
            graphics.drawString(font, if (banned) I18n.get("cobblemon.stadium.picker.banned") else I18n.get("cobblemon.stadium.picker.allowed"), detailLeft + 6, bottom - 11, if (banned) StadiumSkin.FOCUS else StadiumSkin.INK, false)
        }
        if (filtered.isEmpty()) graphics.drawString(font, I18n.get("cobblemon.stadium.picker.no_matches"), left + 6, top + 8, StadiumSkin.MUTED, false)
        if (layout.maxOffset(filtered.size) > 0) {
            val trackHeight = bottom - top
            val thumbHeight = (trackHeight * visibleRows / layout.totalRows(filtered.size)).coerceAtLeast(8)
            val thumbY = top + (trackHeight - thumbHeight) * offset / layout.maxOffset(filtered.size)
            StadiumSkin.panel(graphics, StadiumSkin.Surface.SELECTED, left + listWidth - 4, thumbY, 3, thumbHeight)
        }
        val count = category.entries(draft).size
        graphics.drawString(font, font.plainSubstrByWidth(status.ifEmpty { I18n.get("cobblemon.stadium.picker.banned_matches_click_to_toggle", count, filtered.size) }, right - left), left, bottom + 2, StadiumSkin.INK, false)
        super.render(graphics, if (openDropdown?.isOpen == true) -1 else mouseX, if (openDropdown?.isOpen == true) -1 else mouseY, partialTick)
        openDropdown?.renderPopup(graphics, mouseX, mouseY)
        if (openDropdown?.isOpen != true && previewIndex >= 0) {
            val entry = filtered.getOrNull(layout.entryIndex(offset, previewIndex))
            if (entry != null && !layout.hasDetail) {
                val lines = if (!entry.item.isEmpty) getTooltipFromItem(minecraft!!, entry.item).toMutableList()
                    else mutableListOf(entry.name, entry.detail)
                lines.add(Component.literal(if (entry.id in category.entries(draft)) I18n.get("cobblemon.stadium.picker.banned_click_to_allow") else I18n.get("cobblemon.stadium.picker.allowed_click_to_ban")))
                val tile = listWidgets[previewIndex]
                graphics.pose().pushPose()
                try {
                    graphics.pose().translate(0.0, 0.0, 2500.0)
                    val wrapped = lines.flatMap { font.split(it, minOf(220, width - 32)) }
                    graphics.renderTooltip(font, wrapped, if (hoveredIndex >= 0) mouseX else tile.x + tile.width, if (hoveredIndex >= 0) mouseY else tile.y)
                } finally {
                    graphics.pose().popPose()
                }
            }
        }
    }

    private fun renderCard(graphics: GuiGraphics, entry: Entry, tile: StadiumButton, partialTick: Float) {
        val x = tile.x
        val y = tile.y
        val w = tile.width
        val h = tile.height
        val banned = entry.id in category.entries(draft)
        StadiumSkin.panel(graphics, StadiumSkin.Surface.CONTROL, x, y, w, h)
        if (entry.species != null) {
            blitk(matrixStack = graphics.pose(), texture = slotTexture, x = x, y = y, width = w, height = h)
            val modelSize = minOf(w - 4, h - 10)
            graphics.enableScissor(x + 2, y + 8, x + w - 2, y + h - 2)
            try {
                drawPokemon(graphics, entry, x + w / 2, y + 8 - modelSize * 12 / 68, modelSize * 40f / 68f, 35f, partialTick)
            } finally {
                graphics.disableScissor()
            }
            graphics.drawString(font, entry.species.nationalPokedexNumber.toString().padStart(4, '0'), x + 2, y + 2, StadiumSkin.INK, false)
        } else if (entry.move != null) {
            val move = entry.move
            val rgb = move.elementalType.hue.toRGB()
            graphics.pose().pushPose()
            graphics.pose().translate(x.toDouble(), y.toDouble(), 0.0)
            graphics.pose().scale(w / 108f, h / 22f, 1f)
            blitk(matrixStack = graphics.pose(), texture = MoveSlotWidget.moveResource, x = 0, y = 0, width = 108, height = 22,
                vOffset = if (tile.isHoveredOrFocused) 22 else 0, textureHeight = 44, red = rgb.first, green = rgb.second, blue = rgb.third)
            blitk(matrixStack = graphics.pose(), texture = MoveSlotWidget.moveOverlayResource, x = 0, y = 0, width = 108, height = 22)
            graphics.pose().popPose()
            blitk(matrixStack = graphics.pose(), texture = MoveSlotWidget.moveOverlayBarResource, x = x + w - 59, y = y + h - 11, width = 58, height = 10)
            TypeIcon(x + 4, y + (h - 18) / 2, move.elementalType).render(graphics)
            MoveCategoryIcon(x + w - 54, y + h - 10, move.damageCategory).render(graphics)
            graphics.drawString(font, font.plainSubstrByWidth(entry.name.string, w - 38), x + 28, y + 4, StadiumSkin.TEXT, false)
            graphics.drawString(font, I18n.get("cobblemon.stadium.picker.pp", move.pp), x + w - 32, y + h - 10, StadiumSkin.TEXT, false)
        } else if (!entry.item.isEmpty) {
            graphics.renderItem(entry.item, x + (w - 16) / 2, y + (h - 16) / 2)
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(entry.name.string, w - 17), x + 5, y + 5, StadiumSkin.INK, false)
            font.split(entry.detail, w - 10).take(((h - 18) / 10).coerceAtLeast(0)).forEachIndexed { index, line ->
                graphics.drawString(font, line, x + 5, y + 17 + index * 10, StadiumSkin.MUTED, false)
            }
        }
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 1500.0)
        if (tile.isHoveredOrFocused) graphics.renderOutline(x, y, w, h, StadiumSkin.INK)
        if (banned) {
            graphics.renderOutline(x, y, w, h, StadiumSkin.FOCUS)
            graphics.fill(x + w - 9, y + 1, x + w - 1, y + 9, StadiumSkin.FOCUS)
            graphics.fill(x + w - 7, y + 4, x + w - 3, y + 6, StadiumSkin.TEXT)
        }
        graphics.pose().popPose()
    }

    private fun drawPokemon(graphics: GuiGraphics, entry: Entry, x: Int, y: Int, scale: Float, yaw: Float, partialTick: Float) {
        val species = entry.species ?: return
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(x.toDouble(), y.toDouble(), 1000.0)
            drawProfilePokemon(RenderablePokemon(species, species.standardForm.aspects.toSet()), graphics.pose(),
                Quaternionf().fromEulerXYZDegrees(Vector3f(13f, yaw, 0f)), state = states.getOrPut(entry.id) { FloatingState() },
                partialTicks = partialTick, scale = scale, profileTransformType = ProfileTransformType.POKEDEX)
        } finally {
            graphics.pose().popPose()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { return it.popupClick(mouseX, mouseY, button) }
        val index = listWidgets.indexOfFirst { it.isMouseOver(mouseX, mouseY) }
        val handled = super.mouseClicked(mouseX, mouseY, button)
        if (handled && index >= 0) focused = listWidgets.getOrNull(index.coerceAtMost(listWidgets.lastIndex))
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { return it.popupScroll(verticalAmount) }
        if (mouseX >= left && mouseX < left + listWidth && mouseY >= top && mouseY < bottom) {
            offset -= verticalAmount.toInt(); rebuildEntries(); return true
        }
        if (layout.hasDetail && mouseX >= detailLeft && mouseX < right && mouseY >= top && mouseY < bottom - 14) {
            val entry = selected ?: return true
            val contentHeight = font.split(entry.name, detailWidth - 12).size * 10 + font.split(entry.detail, detailWidth - 12).size * 10 +
                if (entry.species != null) modelHeight + 12 else if (!entry.item.isEmpty) 23 else 0
            detailScroll = (detailScroll - (verticalAmount * 12).toInt()).coerceIn(0, (contentHeight - (bottom - top - 24)).coerceAtLeast(0))
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0 && mouseX >= detailLeft && mouseX < right && mouseY >= top && mouseY < bottom && selected?.species != null) {
            rotation += dragX.toFloat(); return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { if (it.keyPressed(keyCode, scanCode, modifiers)) return true }
        val focusedIndex = listWidgets.indexOfFirst { it.isFocused }
        if (focusedIndex >= 0 && keyCode in listOf(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN)) {
            val delta = when (keyCode) {
                GLFW.GLFW_KEY_LEFT -> -1
                GLFW.GLFW_KEY_RIGHT -> 1
                GLFW.GLFW_KEY_UP -> -layout.columns
                else -> layout.columns
            }
            val target = (layout.entryIndex(offset, focusedIndex) + delta).coerceIn(0, filtered.lastIndex)
            if (target < offset * layout.columns) offset = target / layout.columns
            if (target >= offset * layout.columns + layout.capacity) offset = target / layout.columns - visibleRows + 1
            rebuildEntries()
            focused = listWidgets.getOrNull(target - offset * layout.columns)
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            offset += visibleRows * if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) 1 else -1
            rebuildEntries(); return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean =
        openDropdown?.takeIf { it.isOpen }?.charTyped(codePoint, modifiers) ?: super.charTyped(codePoint, modifiers)

    override fun onClose() { minecraft?.setScreen(parent) }
    override fun isPauseScreen() = false
}
