/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.ObjectSelectionList.Entry
import net.minecraft.util.Mth

abstract class ScrollingWidget<T : Entry<T>>(
    top: Int = 0,
    val left: Int = 0,
    width: Int = 10,
    height: Int = 10,
    slotHeight: Int = 10,
    val scrollBarWidth: Int = 5
) : ObjectSelectionList<T>(
    Minecraft.getInstance(),
    width, // Width
    height, // Height
    top, // Top
    slotHeight // Slot Height
), CobblemonRenderable {

    override fun extractListBackground(graphics: GuiGraphicsExtractor) {}
    override fun extractSelection(guiGraphics: GuiGraphicsExtractor, entry: T, i: Int) {}

    init {
        setRectangle(width, height, top, top + height)
        setLeft(left)
    }

    final override fun setRectangle(width: Int, height: Int, top: Int, bottom: Int) {
        this.width = width
        this.height = height
        this.y = bottom
        this.x = left + width
    }

    fun setLeft(left: Int){
        this.x = left
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        this.setFocused(
            if (this.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
                this.getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble())
            } else {
                null
            }
        )

        this.enableScissor(context)

        this.extractListItems(context, mouseX, mouseY, delta)
        context.disableScissor()


        val maxScroll = this.maxScrollAmount()
        if (maxScroll > 0) {
            renderScrollbar(context, mouseX, mouseY, delta)
        }
    }

    open fun renderScrollbar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float){
        val xLeft = this.rowRight
        val xRight = this.rowRight + scrollBarWidth

        val barHeight = this.bottom - this.y

        var j2 = ((barHeight * barHeight).toFloat() / this.maxScrollAmount().toFloat()).toInt()
        j2 = Mth.clamp(j2, 32, barHeight - 8)
        var k1 = scrollAmount().toInt() * (barHeight - j2) / this.maxScrollAmount() + this.y
        if (k1 < this.y) {
            k1 = this.y
        }

        context.fill(xLeft, this.y, xRight, this.bottom, -16777216)
        context.fill(xLeft, k1, xRight, k1 + j2, -8355712)
        context.fill(xLeft ,k1, xRight - 1, k1 + j2 - 1, -4144960)
    }

    override fun getRowLeft(): Int {
        return this.left
    }

    override fun getRowRight(): Int {
        return this.rowLeft + this.rowWidth
    }

    override fun getRowWidth(): Int {
        return this.width
    }

    override fun getRowTop(index: Int): Int {
        return this.y - scrollAmount().toInt() + (index * this.defaultEntryHeight)
    }

    override fun getRowBottom(index: Int): Int {
        return this.getRowTop(index) + this.defaultEntryHeight
    }

    override fun scrollBarX(): Int = this.left + this.width - this.scrollBarWidth

    abstract class Slot<T : Slot<T>>(): Entry<T>() {
        // Override render to show each individual element
    }
}
