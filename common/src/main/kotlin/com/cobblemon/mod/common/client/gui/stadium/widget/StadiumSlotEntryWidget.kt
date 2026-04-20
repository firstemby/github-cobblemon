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
import net.minecraft.world.phys.Vec3

class StadiumSlotEntryWidget(
    x: Int, y: Int, width: Int,
    private val label: String,
    private val position: Vec3,
    private val indicatorColor: Int,
    var isSelected: Boolean = false,
    private val onWand: () -> Unit,
    private val onDelete: () -> Unit,
    private val onSelect: () -> Unit
) : AbstractWidget(x, y, width, ENTRY_HEIGHT, Component.empty()), CobblemonRenderable {

    companion object {
        const val ENTRY_HEIGHT = 16
        private const val BG_COLOR = 0xFF2A2A44.toInt()
        private const val BG_SELECTED_COLOR = 0xFF3A3A5C.toInt()
        private const val WAND_BUTTON_COLOR = 0xFF33446A.toInt()
        private const val WAND_HOVER_COLOR = 0xFF4455AA.toInt()
        private const val DELETE_COLOR = 0xFFFF6644.toInt()
    }

    private val wandBtnWidth = 30
    private val deleteBtnWidth = 14
    private val coordsText = "%.0f, %.0f, %.0f".format(position.x, position.y, position.z)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
        val bgColor = if (isSelected) BG_SELECTED_COLOR else if (hovered) 0xFF333355.toInt() else BG_COLOR

        // Background
        context.fill(x, y, x + width, y + ENTRY_HEIGHT - 2, bgColor)
        if (isSelected) {
            context.renderOutline(x, y, width, ENTRY_HEIGHT - 2, indicatorColor)
        }

        // Color indicator bar
        context.fill(x + 2, y + 2, x + 5, y + ENTRY_HEIGHT - 4, indicatorColor)

        // Label
        drawScaledText(
            context = context,
            text = Component.literal(label),
            x = x + 8,
            y = y + 2,
            scale = 0.8f,
            colour = if (isSelected) 0xFFFFFF else 0xA0A0B8,
            shadow = true
        )

        // Coordinates
        drawScaledText(
            context = context,
            text = Component.literal(coordsText),
            x = x + 8,
            y = y + 8,
            scale = 0.6f,
            colour = 0x666666,
            shadow = true
        )

        // Wand button
        val wandX = x + width - wandBtnWidth - deleteBtnWidth - 6
        val wandHovered = mouseX in wandX..(wandX + wandBtnWidth) && mouseY in y..(y + ENTRY_HEIGHT - 2)
        context.fill(wandX, y + 1, wandX + wandBtnWidth, y + ENTRY_HEIGHT - 3, if (wandHovered) WAND_HOVER_COLOR else WAND_BUTTON_COLOR)
        drawScaledText(
            context = context,
            text = Component.literal("Wand"),
            x = wandX + wandBtnWidth / 2,
            y = y + 4,
            scale = 0.7f,
            colour = 0x88AAFF,
            centered = true,
            shadow = true
        )

        // Delete button
        val delX = x + width - deleteBtnWidth - 2
        val delHovered = mouseX in delX..(delX + deleteBtnWidth) && mouseY in y..(y + ENTRY_HEIGHT - 2)
        drawScaledText(
            context = context,
            text = Component.literal("\u2715"),
            x = delX + deleteBtnWidth / 2,
            y = y + 3,
            colour = if (delHovered) 0xFFFFFF else DELETE_COLOR,
            centered = true,
            shadow = true
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        val wandX = x + width - wandBtnWidth - deleteBtnWidth - 6
        val delX = x + width - deleteBtnWidth - 2

        return when {
            mouseX.toInt() in wandX..(wandX + wandBtnWidth) -> { onWand(); true }
            mouseX.toInt() in delX..(delX + deleteBtnWidth) -> { onDelete(); true }
            else -> { onSelect(); true }
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
