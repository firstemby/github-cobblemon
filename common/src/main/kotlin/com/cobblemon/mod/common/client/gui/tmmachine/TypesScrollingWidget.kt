/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.tmmachine.TypesScrollingWidget.ScrollSlotRow
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.util.FastColor
import net.minecraft.util.Mth

class TypesScrollingWidget(val pX: Int, val pY: Int, val setType: (ElementalType?) -> (Unit)): ScrollingWidget<ScrollSlotRow>(
    width = WIDTH,
    height = HEIGHT,
    left = pX,
    top = pY - HEIGHT,
    slotHeight = SLOT_SIZE + SLOT_SPACING
) {
    companion object {
        const val WIDTH = 112
        const val HEIGHT = 100
        const val SLOT_SIZE = 22
        const val SLOT_SPACING = 5
    }

    init {
        (listOf(null) + ElementalTypes.all()).chunked(4).forEachIndexed { index, listChunk ->
            addEntry(ScrollSlotRow(listChunk, setType))
        }
    }

    override fun getScrollbarPosition(): Int = rowLeft + width - 3

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val xRight = xLeft + 3
        val barHeight = this.bottom - this.y

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
        if (yTop < this.y) {
            yTop = this.y
        }

        context.fill(xLeft, this.y, xRight, this.bottom, FastColor.ARGB32.color(255, 75, 75, 75)) // background
        context.fill(xLeft,yTop, xRight, yTop + yBottom, FastColor.ARGB32.color(255, 141, 141, 141)) // base
    }

    fun renderEntry(context: GuiGraphics, mouseX: Int, mouseY: Int, index: Int, x: Int, y: Int) {
        val entry =  this.getEntry(index)
        entry.x = x
        entry.y = y
        entry.renderRow( context, y, x, mouseX, mouseY)
    }

    override fun getEntry(index: Int): ScrollSlotRow = children()[index] as ScrollSlotRow

    override fun renderListItems(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val rowX = rowLeft

        for (index in 0 until this.itemCount) {
            val rowY = this.getRowTop(index)
            val rowBottom = this.getRowBottom(index)
            if (rowBottom >= this.y && rowY <= this.bottom) {
                this.renderEntry(context!!, mouseX, mouseY, index, rowX, rowY)
            }
        }
    }

    class ScrollSlotRow(val types:  List<ElementalType?>, val setType : (ElementalType?) -> Unit): Slot<ScrollSlotRow>() {
        companion object {
            private val allSlotResource = cobblemonResource("textures/gui/tmmachine/type_slot_all.png")
            private val slotResource = cobblemonResource("textures/gui/tmmachine/type_slot.png")
        }

        var x: Int = 0
        var y: Int = 0

        fun renderRow(context: GuiGraphics, y: Int, x: Int, mouseX: Int, mouseY: Int) {
            types.forEachIndexed { index, type ->
                val matrices = context.pose()

                val startPosX = x + ((SLOT_SPACING + SLOT_SIZE) * index)
                val startPosY = y + SLOT_SPACING

                val slotHovered = getHoveredSlotIndex(mouseX, mouseY) == index

                blitk(
                    matrixStack = matrices,
                    texture = if (type != null) slotResource else allSlotResource,
                    x = startPosX,
                    y = startPosY,
                    width = SLOT_SIZE,
                    height = SLOT_SIZE,
                    vOffset = if (slotHovered) SLOT_SIZE else 0,
                    textureHeight = SLOT_SIZE * 2
                )

                if (type != null) {
                    TypeIcon(startPosX + 2, startPosY + 2, type).render(context)
                }
            }
        }

        override fun render(context: GuiGraphics, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {}

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val hoverIndex = getHoveredSlotIndex(mouseX.toInt(), mouseY.toInt())
            if (hoverIndex > -1 && hoverIndex < types.size) {
                setType.invoke(types[hoverIndex])
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
            }
            return true
        }

        private fun getHoveredSlotIndex(mouseX: Int, mouseY: Int): Int {
            types.forEachIndexed { index, _ ->
                val startPosX = x + ((SLOT_SPACING + SLOT_SIZE) * index)
                val startPosY = y + SLOT_SPACING + 1

                if (mouseX in startPosX..(startPosX + SLOT_SIZE) && mouseY in startPosY..(startPosY + SLOT_SIZE)) {
                    return index
                }
            }
            return -1
        }

        override fun getNarration(): Component {
            if (types.isNotEmpty()) {
                return "${types[0]}-${types[types.size - 1]}".text()
            }
            return "".text()
        }
    }
}
