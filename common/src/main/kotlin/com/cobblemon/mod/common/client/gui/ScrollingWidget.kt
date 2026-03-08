/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.util.Mth

abstract class ScrollingWidget<T : ObjectSelectionList.Entry<T>>(
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

    override fun renderListBackground(guiGraphics: GuiGraphics) {}
    override fun renderSelection(guiGraphics: GuiGraphics, entry: T, i: Int) {}

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

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        this.focused = if (this.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) this.getEntryAtPosition(
            mouseX.toDouble(),
            mouseY.toDouble()
        ) else null
        val l1 = this.rowLeft
        val l = this.y + 4 - scrollAmount.toInt()

        this.enableScissor(context)

        this.renderListItems(context, mouseX, mouseY, delta)
        context.disableScissor()


        val maxScroll = this.maxScrollAmount()
        if (maxScroll > 0) {
            renderScrollbar(context, mouseX, mouseY, delta)
        }
    }

    open fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float){
        val xLeft = this.scrollBarY()
        val xRight = xLeft + scrollBarWidth

        val barHeight = this.bottom - this.y

        var j2 = ((barHeight * barHeight).toFloat() / this.maxScrollAmount().toFloat()).toInt()
        j2 = Mth.clamp(j2, 32, barHeight - 8)
        var k1 = scrollAmount.toInt() * (barHeight - j2) / this.maxScrollAmount() + this.y
        if (k1 < this.y) {
            k1 = this.y
        }

        context.fill(xLeft, this.y, xRight, this.bottom, -16777216)
        context.fill(xLeft, k1, xRight, k1 + j2, -8355712)
        context.fill(xLeft ,k1, xRight - 1, k1 + j2 - 1, -4144960)
    }

    override fun renderListItems(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val i = this.rowLeft
        val j = this.rowWidth
        val k = this.defaultEntryHeight
        val l = this.itemCount

        for (m in 0 until l) {
            val n = this.getRowTop(m)
            val o = this.getRowBottom(m)
            if (o >= this.y && n <= this.bottom) {
                this.renderItem(context, mouseX, mouseY, delta, m, i, n, j, k)
            }
        }
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
        return this.y - scrollAmount.toInt() + (index * this.defaultEntryHeight)
    }

    override fun getRowBottom(index: Int): Int {
        return this.getRowTop(index) + this.defaultEntryHeight
    }

    override fun scrollBarX(): Int = this.left + this.width - this.scrollBarWidth

    abstract class Slot<T : Slot<T>>(): Entry<T>() {
        // Override render to show each individual element
    }
}
