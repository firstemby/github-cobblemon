/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.marks

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.mark.Mark
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.common.MarkIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.marks.MarksScrollingWidget.ScrollSlotRow
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.Mth

class MarksScrollingWidget(val pX: Int, val pY: Int, val setSelectedMark: () -> (Unit), val setHoveredMark: (Mark?) -> (Unit)): ScrollingWidget<ScrollSlotRow>(
    width = WIDTH,
    height = HEIGHT,
    left = pX,
    top = pY - HEIGHT,
    slotHeight = SLOT_SIZE + SLOT_SPACING
) {
    companion object {
        const val WIDTH = 119
        const val HEIGHT = 98
        const val SLOT_SIZE = 16
        const val SLOT_SPACING = 3
    }

    fun createEntries(marks: List<Mark?>) {
        marks.chunked(6).forEachIndexed { index, listChunk ->
            addEntry(ScrollSlotRow(this, listChunk, setSelectedMark, setHoveredMark))
        }
    }

    override fun getRowLeft(): Int = if (children().size > 5) (left + 1) else left

    override fun scrollBarX(): Int = rowLeft + width - 3

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Do not render scrollbar if all rows are already visible
        if (children().size > 5) {
            val xLeft = this.scrollBarX()
            val xRight = xLeft + 3

            val barHeight = this.bottom - this.y

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxScrollAmount().toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScrollAmount() + this.y
            if (yTop < this.y) {
                yTop = this.y
            }

            context.fill(xLeft, this.y, xRight, this.bottom, ARGB.color(255, 75, 75, 75)) // background
            context.fill(xLeft,yTop, xRight, yTop + yBottom, ARGB.color(255, 141, 141, 141)) // base
        }
    }

    fun renderEntry(context: GuiGraphics, mouseX: Int, mouseY: Int, index: Int, x: Int, y: Int): Boolean {
        val entry =  this.getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble())!!
        entry.x = x
        entry.y = y
        return entry.renderRow( context, y, x, mouseX, mouseY)
    }

    override fun renderListItems(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val rowX = rowLeft

        var anyHovered = false
        for (index in 0 until this.itemCount) {
            val rowY = this.getRowTop(index)
            val o = this.getRowBottom(index)
            if (o >= this.y && rowY <= this.bottom) {
                if (this.renderEntry(context!!, mouseX, mouseY, index, rowX, rowY)) anyHovered = true
            }
        }
        if (!anyHovered) setHoveredMark(null)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragY: Double, dragX: Double): Boolean {
        // Prevent scrollbar drag if all rows are already visible
        return if (children().size > 5) super.mouseDragged(mouseButtonEvent, dragY, dragX) else true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // Prevent scroll if all rows are already visible
        return if (children().size > 5) super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) else true
    }

    class ScrollSlotRow(
        val parent: MarksScrollingWidget,
        val markList:  List<Mark?>,
        val setSelectedMark : () -> Unit,
        val setHoveredMark: (Mark?) -> Unit
    ): Slot<ScrollSlotRow>() {
        companion object {
            private val slotResource = cobblemonResource("textures/gui/summary/summary_mark_slot.png")
        }

        var x: Int = 0
        var y: Int = 0

        fun renderRow(context: GuiGraphics, y: Int, x: Int, mouseX: Int, mouseY: Int): Boolean {
            var anyHovered = false
            val horizontalSpacing = if (isScrollVisible()) SLOT_SPACING else (SLOT_SPACING + 1)
            markList.forEachIndexed { index, mark ->
                val matrices = context.pose()

                val startPosX = x + ((horizontalSpacing + SLOT_SIZE) * index)
                val startPosY = y + SLOT_SPACING

                val slotHovered = mark != null && getHoveredSlotIndex(mouseX, mouseY) == index

                if (slotHovered) {
                    setHoveredMark(markList[index])
                    anyHovered = true
                }

                blitk(
                    matrixStack = matrices,
                    texture = slotResource,
                    x = startPosX,
                    y = startPosY,
                    width = SLOT_SIZE,
                    height = SLOT_SIZE,
                    vOffset = if (slotHovered) SLOT_SIZE else 0,
                    textureHeight = SLOT_SIZE * 2
                )

                if (mark != null) MarkIcon(startPosX, startPosY, mark, false).render(context, mouseX, mouseY)
            }
            return anyHovered
        }

        override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
            setSelectedMark.invoke()
            return true
        }

        private fun isScrollVisible(): Boolean = parent.children().size > 5

        private fun getHoveredSlotIndex(mouseX: Int, mouseY: Int): Int {
            val horizontalSpacing = if (isScrollVisible()) SLOT_SPACING else (SLOT_SPACING + 1)
            markList.forEachIndexed { index, _ ->
                val startPosX = x + ((horizontalSpacing + SLOT_SIZE) * index)
                val startPosY = y + SLOT_SPACING + 1

                if (mouseX in startPosX..(startPosX + SLOT_SIZE)
                    && mouseY in startPosY..(startPosY + SLOT_SIZE)) {
                    return index
                }
            }
            return -1
        }

        override fun getNarration(): Component {
            if (markList.isNotEmpty()) {
                return "${markList[0]}-${markList[markList.size - 1]}".text()
            }
            return "".text()
        }

        override fun renderContent(
            guiGraphics: GuiGraphics,
            i: Int,
            j: Int,
            bl: Boolean,
            f: Float
        ) {
        }
    }
}
