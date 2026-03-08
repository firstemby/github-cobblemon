/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves

import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class MoveDescriptionScrollList(
    private val listX: Int,
    private val listY: Int,
    slotHeight: Int
) : ObjectSelectionList<MoveDescriptionEntry>(
    Minecraft.getInstance(),
    60, // width
    30, // height
    0, // top
    slotHeight
), CobblemonRenderable {
    private var scrolling = false

    init {
        this.y = this.listY
        this.x = this.listX
    }

    override fun scrollBarX(): Int {
        return x + width - 3
    }

    override fun renderWidget(context: GuiGraphics, pMouseX: Int, pMouseY: Int, f: Float) {
        isHovered = pMouseX >= x && pMouseY >= y && pMouseX < x + width && pMouseY < y + height
        context.enableScissor(
            x,
            y,
            x + width,
            y + height
        )
        super.renderWidget(context, pMouseX, pMouseY, f)
        context.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        updateScrollingState(mouseButtonEvent.x, mouseButtonEvent.y)
        if (scrolling) isDragging = true

        return isDragging
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaY: Double, deltaX: Double): Boolean {
        if (scrolling) {
            if (mouseButtonEvent.y < y) {
                scrollAmount = 0.0
            } else if (mouseButtonEvent.y > bottom) {
                scrollAmount = maxScrollAmount().toDouble()
            } else {
                scrollAmount += deltaY
            }
        }
        return super.mouseDragged(mouseButtonEvent, deltaY, deltaX)
    }

    override fun renderListBackground(guiGraphics: GuiGraphics) {}

    private fun updateScrollingState(mouseX: Double, mouseY: Double) {
        scrolling = mouseX >= scrollBarX().toDouble()
                && mouseX < (scrollBarX() + 3).toDouble()
                && mouseY >= y
                && mouseY < bottom
    }

    fun setMoveDescription(moveDescription: MutableComponent) {
        clearEntries()
        val splitWidth = 100
        val text = moveDescription.string
        val words = text.split(" ")
        val splitText = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (Minecraft.getInstance().font.width(currentLine.toString() + word) > splitWidth) {
                splitText.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            splitText.add(currentLine.toString())
        }

        for (part in splitText) {
            addEntry(MoveDescriptionEntry(Component.literal(part)))
        }
    }
}
