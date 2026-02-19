/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class PokeEditLocatorListWidget(
    val pX: Int,
    val pY: Int,
    val listWidth: Int,
    val listHeight: Int,
    private val onSelectLocator: (String?) -> Unit
) : ScrollingWidget<PokeEditLocatorListWidget.LocatorRow>(
    left = pX,
    top = pY - listHeight,
    width = listWidth,
    height = listHeight,
    slotHeight = ROW_HEIGHT
) {
    companion object {
        private const val ROW_HEIGHT = 9
        private const val TEXT_SCALE = 0.5F
        private const val BORDER_COLOR = 0xFF1B1B1B.toInt()
        private const val FILL_COLOR = 0xFF3B3B3B.toInt()
        private const val TEXT_COLOR = 0xFFFCFCFC.toInt()
    }

    private val listTop = pY - listHeight
    private val listBottom = pY

    private var selectedLocator: String? = null

    fun setLocators(locators: Collection<String>) {
        clearEntries()
        locators.sorted().forEach { addEntry(LocatorRow(it)) }
        if (selectedLocator != null && locators.none { it == selectedLocator }) {
            selectedLocator = null
            onSelectLocator(null)
        }
        scrollAmount = 0.0
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(pX - 1, listTop - 1, pX + listWidth + 1, listBottom + 1, BORDER_COLOR)
        context.fill(pX, listTop, pX + listWidth, listBottom, FILL_COLOR)

        focused = if (mouseX in pX..(pX + listWidth) && mouseY in listTop..listBottom) {
            getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble())
        } else {
            null
        }

        context.enableScissor(pX, listTop, pX + listWidth, listBottom)
        renderListItems(context, mouseX, mouseY, delta)
        context.disableScissor()

        if (maxScroll > 0) {
            renderScrollbar(context, mouseX, mouseY, delta)
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, verticalAmount: Double): Boolean {
        if (mouseX !in pX.toDouble()..(pX + listWidth).toDouble()
            || mouseY !in listTop.toDouble()..listBottom.toDouble()
        ) {
            return false
        }
        val maxScroll = (itemCount * itemHeight - listHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return true
        val step = if (verticalAmount != 0.0) verticalAmount else amount
        scrollAmount = Mth.clamp(scrollAmount - step * itemHeight, 0.0, maxScroll.toDouble())
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX !in pX.toDouble()..(pX + listWidth).toDouble()
            || mouseY !in listTop.toDouble()..listBottom.toDouble()
        ) {
            return false
        }
        if (button != 0) {
            return false
        }
        val localY = mouseY.toInt() - listTop + scrollAmount.toInt()
        if (localY < 0) return false
        val index = localY / itemHeight
        if (index !in 0 until itemCount) return false
        val entry = getEntry(index)
        if (entry is LocatorRow) {
            selectedLocator = entry.name
            onSelectLocator(selectedLocator)
            return true
        }
        return false
    }

    override fun renderListItems(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val rowX = rowLeft
        for (index in 0 until itemCount) {
            val rowY = getRowTop(index)
            val rowBottom = getRowBottom(index)
            if (rowBottom >= listTop && rowY <= listBottom) {
                val entry = getEntry(index)
                if (entry is LocatorRow) {
                    entry.isSelected = entry.name == selectedLocator
                }
                renderItem(context, mouseX, mouseY, delta, index, rowX, rowY, rowWidth, itemHeight)
            }
        }
    }

    override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = this.scrollbarPosition
        val xRight = xLeft + 3
        val yStart = listTop
        val barHeight = listBottom - listTop

        var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
        yBottom = Mth.clamp(yBottom, 16, barHeight - 4)
        var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + yStart
        if (yTop < yStart) yTop = yStart

        context.fill(xLeft + 1, yStart, xRight - 1, listBottom, 0xFF7AEEF1.toInt())
        context.fill(xLeft, yTop, xRight, yTop + yBottom, 0xFF3A96B6.toInt())
    }

    override fun getRowTop(index: Int): Int {
        return listTop - scrollAmount.toInt() + (index * itemHeight)
    }

    override fun getRowBottom(index: Int): Int {
        return getRowTop(index) + itemHeight
    }

    override fun getBottom(): Int {
        return listBottom
    }

    class LocatorRow(val name: String) : Slot<LocatorRow>() {
        var isSelected: Boolean = false

        override fun render(
            context: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            val bg = when {
                isSelected -> 0xFF4A4A4A.toInt()
                hovered -> 0xFF2F2F2F.toInt()
                else -> null
            }
            if (bg != null) {
                context.fill(x + 1, y, x + entryWidth - 1, y + ROW_HEIGHT, bg)
            }
            drawScaledText(
                context = context,
                text = name.text(),
                x = x + 3,
                y = y + 2,
                scale = TEXT_SCALE,
                colour = TEXT_COLOR,
                shadow = false
            )
        }

        override fun getNarration(): Component {
            return Component.literal(name)
        }
    }
}
