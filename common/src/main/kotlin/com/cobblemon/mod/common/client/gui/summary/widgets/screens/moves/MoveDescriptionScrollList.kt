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
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
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

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, pMouseX: Int, pMouseY: Int, f: Float) {
        context.enableScissor(
            x,
            y,
            x + width,
            y + height
        )
        super.extractWidgetRenderState(context, pMouseX, pMouseY, f)
        context.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        updateScrollingState(mouseButtonEvent.x(), mouseButtonEvent.y())
        if (scrolling) isDragging = true

        return isDragging
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mouseY = mouseButtonEvent.y()
        if (scrolling) {
            if (mouseY < y) {
                setScrollAmount(0.0)
            } else if (mouseY > bottom) {
                setScrollAmount(maxScrollAmount().toDouble())
            } else {
                setScrollAmount(scrollAmount() + deltaY)
            }
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY)
    }

    override fun extractListBackground(guiGraphics: GuiGraphicsExtractor) {}

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
