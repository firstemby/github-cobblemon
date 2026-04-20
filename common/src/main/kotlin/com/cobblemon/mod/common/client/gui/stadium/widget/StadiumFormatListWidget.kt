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
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

data class FormatEntry(
    val id: String,
    val displayName: String,
    val isPreset: Boolean,
    var isEnabled: Boolean
)

class StadiumFormatListWidget(
    x: Int, y: Int, width: Int, height: Int,
    private val entries: MutableList<FormatEntry>,
    private val onSelect: (FormatEntry) -> Unit,
    private val onToggle: (FormatEntry) -> Unit,
    private val onAddCustom: () -> Unit
) : AbstractWidget(x, y, width, height, Component.empty()), CobblemonRenderable {

    companion object {
        private const val ENTRY_HEIGHT = 16
        private const val HEADER_HEIGHT = 14
        private const val TOGGLE_HIT_WIDTH = 16
        private const val BG_COLOR = 0xFF1E1E36.toInt()
        private const val BORDER_COLOR = 0xFF3A3A5C.toInt()
        private const val ENTRY_COLOR = 0xFF2A2A44.toInt()
        private const val ENTRY_SELECTED_COLOR = 0xFF3A4A7A.toInt()
        private const val ENTRY_HOVER_COLOR = 0xFF333355.toInt()
        private const val ACCENT_COLOR = 0xFF6688FF.toInt()
        private const val ADD_BUTTON_COLOR = 0xFF33446A.toInt()
    }

    var selectedId: String? = null
    private var scrollOffset = 0
    private val maxVisible get() = (height - HEADER_HEIGHT - ENTRY_HEIGHT - 10) / ENTRY_HEIGHT

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR)
        context.renderOutline(x, y, width, height, BORDER_COLOR)

        // Header
        drawScaledText(
            context = context,
            text = Component.literal("FORMATS"),
            x = x + 8,
            y = y + 4,
            scale = 0.7f,
            colour = ACCENT_COLOR,
            shadow = true
        )

        // Entries
        val visible = entries.drop(scrollOffset).take(maxVisible)
        var entryY = y + HEADER_HEIGHT
        for (entry in visible) {
            val isSelected = entry.id == selectedId
            val isHovered = mouseX in x..(x + width) && mouseY in entryY..(entryY + ENTRY_HEIGHT - 2)
            val bgColor = when {
                isSelected -> ENTRY_SELECTED_COLOR
                isHovered -> ENTRY_HOVER_COLOR
                else -> ENTRY_COLOR
            }

            context.fill(x + 4, entryY, x + width - 4, entryY + ENTRY_HEIGHT - 2, bgColor)
            if (isSelected) {
                context.fill(x + 4, entryY, x + 6, entryY + ENTRY_HEIGHT - 2, ACCENT_COLOR)
            }

            // Name
            drawScaledText(
                context = context,
                text = Component.literal(entry.displayName),
                x = x + 10,
                y = entryY + 4,
                scale = 0.8f,
                colour = if (isSelected) 0xFFFFFF else 0xA0A0B8,
                shadow = true
            )

            // Toggle indicator
            val toggleX = x + width - 16
            val indicatorColor = if (entry.isEnabled) ACCENT_COLOR else 0xFF555555.toInt()
            val indicatorText = if (entry.isEnabled) "\u25CF" else "\u25CB"
            drawScaledText(
                context = context,
                text = Component.literal(indicatorText),
                x = toggleX,
                y = entryY + 3,
                colour = indicatorColor,
                shadow = true
            )

            entryY += ENTRY_HEIGHT
        }

        // Divider before add button
        val dividerY = entryY + 2
        context.fill(x + 8, dividerY, x + width - 8, dividerY + 1, BORDER_COLOR)

        // "+ Custom Format" button
        val addY = dividerY + 4
        val addHovered = mouseX in (x + 4)..(x + width - 4) && mouseY in addY..(addY + ENTRY_HEIGHT - 2)
        context.fill(x + 4, addY, x + width - 4, addY + ENTRY_HEIGHT - 2, if (addHovered) ENTRY_HOVER_COLOR else ADD_BUTTON_COLOR)
        drawScaledText(
            context = context,
            text = Component.literal("+ Custom Format"),
            x = x + width / 2,
            y = addY + 4,
            scale = 0.8f,
            colour = 0x88AAFF,
            centered = true,
            shadow = true
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        // Check entry clicks
        val visible = entries.drop(scrollOffset).take(maxVisible)
        var entryY = y + HEADER_HEIGHT
        for (entry in visible) {
            if (mouseY.toInt() in entryY..(entryY + ENTRY_HEIGHT - 2)) {
                if (mouseX.toInt() > x + width - TOGGLE_HIT_WIDTH - 4) {
                    entry.isEnabled = !entry.isEnabled
                    onToggle(entry)
                } else {
                    selectedId = entry.id
                    onSelect(entry)
                }
                return true
            }
            entryY += ENTRY_HEIGHT
        }

        // Check add button click
        val dividerY = entryY + 2
        val addY = dividerY + 4
        if (mouseY.toInt() in addY..(addY + ENTRY_HEIGHT - 2)) {
            onAddCustom()
            return true
        }

        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, (entries.size - maxVisible).coerceAtLeast(0))
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
