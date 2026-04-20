/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class StadiumOverlayWidget(
    x: Int, y: Int,
    width: Int, height: Int,
    private val title: String,
    private val allItems: List<String>,
    private val selectedItems: MutableSet<String>,
    private val tagColor: Int,
    private val onClose: () -> Unit,
    private val addWidget: (AbstractWidget) -> AbstractWidget
) : AbstractWidget(x, y, width, height, Component.empty()), CobblemonRenderable {

    companion object {
        private const val BG_COLOR = 0xFF1E1E36.toInt()
        private const val BORDER_COLOR = 0xFF6688FF.toInt()
        private const val ENTRY_HEIGHT = 14
        private const val HEADER_HEIGHT = 20
        private const val SEARCH_HEIGHT = 16
        private const val TAG_HEIGHT = 14
        private const val CLOSE_HIT_SIZE = 12
    }

    private var searchText = ""
    private var scrollOffset = 0
    private val searchField: EditBox

    init {
        searchField = EditBox(
            Minecraft.getInstance().font,
            x + 8, y + HEADER_HEIGHT + 4,
            width - 16, SEARCH_HEIGHT,
            Component.translatable("cobblemon.stadium_config.search")
        )
        searchField.setMaxLength(50)
        searchField.setResponder { searchText = it; scrollOffset = 0 }
        addWidget(searchField)
    }

    private val tagAreaTop get() = y + HEADER_HEIGHT + SEARCH_HEIGHT + 10
    private val listAreaTop get() = tagAreaTop + if (selectedItems.isEmpty()) 0 else TAG_HEIGHT + 8
    private val maxVisibleItems get() = (y + height - listAreaTop - 24) / ENTRY_HEIGHT

    private val filteredItems: List<String>
        get() {
            val available = allItems.filter { it !in selectedItems }
            return if (searchText.isBlank()) available
            else available.filter { it.contains(searchText, ignoreCase = true) }
        }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dim background (full screen)
        val mc = Minecraft.getInstance()
        context.fill(0, 0, mc.window.guiScaledWidth, mc.window.guiScaledHeight, 0xE0000000.toInt())

        // Panel background
        context.fill(x, y, x + width, y + height, BG_COLOR)
        context.renderOutline(x, y, width, height, BORDER_COLOR)

        // Title
        drawScaledText(
            context = context,
            text = Component.literal(title),
            x = x + 8,
            y = y + 6,
            colour = 0xFFFFFF,
            shadow = true
        )

        // Close button
        val closeX = x + width - CLOSE_HIT_SIZE - 4
        val closeHovered = mouseX in closeX..(closeX + CLOSE_HIT_SIZE) && mouseY in y..(y + HEADER_HEIGHT)
        drawScaledText(
            context = context,
            text = Component.literal("\u2715"),
            x = closeX + CLOSE_HIT_SIZE / 2,
            y = y + 6,
            colour = if (closeHovered) 0xFFFFFF else 0x888888,
            centered = true,
            shadow = true
        )

        // Search field rendered by widget system

        // Selected tags
        if (selectedItems.isNotEmpty()) {
            var tagX = x + 8
            var tagY = tagAreaTop
            for (item in selectedItems) {
                val label = formatName(item) + " \u2715"
                val tagWidth = Minecraft.getInstance().font.width(label) + 8
                if (tagX + tagWidth > x + width - 8) {
                    tagX = x + 8
                    tagY += TAG_HEIGHT + 2
                }
                context.fill(tagX, tagY, tagX + tagWidth, tagY + TAG_HEIGHT - 2, tagColor)
                drawScaledText(
                    context = context,
                    text = Component.literal(label),
                    x = tagX + 4,
                    y = tagY + 2,
                    scale = 0.8f,
                    colour = 0xFFFFFF,
                    shadow = true
                )
                tagX += tagWidth + 3
            }
        }

        // Available list
        val filtered = filteredItems
        val visible = filtered.drop(scrollOffset).take(maxVisibleItems)
        var itemY = listAreaTop
        for (item in visible) {
            val hovered = mouseX in (x + 8)..(x + width - 8) && mouseY in itemY..(itemY + ENTRY_HEIGHT - 2)
            if (hovered) {
                context.fill(x + 8, itemY, x + width - 8, itemY + ENTRY_HEIGHT - 2, 0xFF333355.toInt())
            }
            drawScaledText(
                context = context,
                text = Component.literal(formatName(item)),
                x = x + 12,
                y = itemY + 2,
                scale = 0.8f,
                colour = 0xA0A0B8,
                shadow = true
            )
            drawScaledText(
                context = context,
                text = Component.literal("+"),
                x = x + width - 16,
                y = itemY + 2,
                colour = if (hovered) 0x88AAFF else 0x6688FF,
                shadow = true
            )
            itemY += ENTRY_HEIGHT
        }

        // Done button
        val doneY = y + height - 22
        val doneHovered = mouseX in (x + 8)..(x + width - 8) && mouseY in doneY..(doneY + 18)
        context.fill(x + 8, doneY, x + width - 8, doneY + 18, if (doneHovered) 0xFF5566BB.toInt() else 0xFF4455AA.toInt())
        drawScaledText(
            context = context,
            text = Component.literal("Done"),
            x = x + width / 2,
            y = doneY + 5,
            colour = 0xFFFFFF,
            centered = true,
            shadow = true
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Close button
        val closeX = x + width - CLOSE_HIT_SIZE - 4
        if (mouseX.toInt() in closeX..(closeX + CLOSE_HIT_SIZE) && mouseY.toInt() in y..(y + HEADER_HEIGHT)) {
            onClose()
            return true
        }

        // Done button
        val doneY = y + height - 22
        if (mouseX.toInt() in (x + 8)..(x + width - 8) && mouseY.toInt() in doneY..(doneY + 18)) {
            onClose()
            return true
        }

        // Tag removal
        if (selectedItems.isNotEmpty() && mouseY.toInt() >= tagAreaTop && mouseY.toInt() < listAreaTop) {
            var tagX = x + 8
            var tagY = tagAreaTop
            for (item in selectedItems.toList()) {
                val label = formatName(item) + " \u2715"
                val tagWidth = Minecraft.getInstance().font.width(label) + 8
                if (tagX + tagWidth > x + width - 8) {
                    tagX = x + 8
                    tagY += TAG_HEIGHT + 2
                }
                if (mouseX.toInt() in tagX..(tagX + tagWidth) && mouseY.toInt() in tagY..(tagY + TAG_HEIGHT - 2)) {
                    selectedItems.remove(item)
                    return true
                }
                tagX += tagWidth + 3
            }
        }

        // Available list clicks
        val filtered = filteredItems
        val visible = filtered.drop(scrollOffset).take(maxVisibleItems)
        var itemY = listAreaTop
        for (item in visible) {
            if (mouseX.toInt() in (x + 8)..(x + width - 8) && mouseY.toInt() in itemY..(itemY + ENTRY_HEIGHT - 2)) {
                selectedItems.add(item)
                return true
            }
            itemY += ENTRY_HEIGHT
        }

        // Pass to search field
        return searchField.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        val maxScroll = (filteredItems.size - maxVisibleItems).coerceAtLeast(0)
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return searchField.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(char: Char, modifiers: Int): Boolean {
        return searchField.charTyped(char, modifiers)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private fun formatName(id: String): String = id.replace("_", " ").replaceFirstChar { it.uppercase() }
}
