/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.api.gui.blitk
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.CommonComponents
import net.minecraft.resources.ResourceLocation
import kotlin.math.floor

open class VariableObjectSelectionList(x: Int, y: Int, width: Int, height: Int): AbstractWidget(x, y, width, height, CommonComponents.EMPTY) {
    val children = mutableListOf<Entry>()

    var listHeight = 0
    var scrollAmount = 0.0

    fun updateScroll(offset: Double) {
        setScroll(scrollAmount + offset)
    }

    fun getMaxScroll(): Int {
        return (listHeight - height).coerceAtLeast(0)
    }

    fun setScroll(scroll: Double) {
        scrollAmount = scroll.coerceIn(0.0, getMaxScroll().toDouble())
    }

    fun removeEntry(index: Int) {
        listHeight -= children[index].height
        children.removeAt(index)
        updatePositions()
    }

    fun removeEntry(entry: Entry) {
        listHeight -= entry.height
        children.remove(entry)
        updatePositions()
    }

    fun removeLastEntry() {
        listHeight -= children.last().height
        children.removeLast()
    }

    fun updatePositions() {
        var stackHeight = 0
        children.forEach {
            it.scrolledY = stackHeight
            stackHeight += it.height
        }
        updateScroll(0.0)
    }

    fun addEntry(entry: Entry) {
        children.add(entry)
        entry.scrolledY = listHeight
        listHeight += entry.height
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.enableScissor(x, y, x + width, y + height)
        children.forEachIndexed { index, entry ->
            val yTop = getRowTop(entry)
            if (yTop + entry.height > y || yTop < y + height) {
                entry.render(guiGraphics, index, x, yTop, mouseX, mouseY, partialTick)
            }
        }
        guiGraphics.disableScissor()
    }

    fun getEntryAtPosition(mouseX: Int, mouseY: Int): Entry? {
        if (mouseX in x..(x + width) && mouseY in y..(y + height)) {
            children.forEach {
                val yTop = getRowTop(it)
                if (mouseY in (yTop)..(yTop + it.height)) {
                    return it
                }
            }
        }
        return null
    }

    fun ensureVisible(index: Int) {
        val entry = children[index]
        ensureVisible(entry)
    }

    open fun ensureVisible(entry: Entry) {
        if (entry !in children) return
        val yTop = entry.scrolledY - scrollAmount.toInt()
        if (yTop < 0) {
            setScroll(entry.scrolledY.toDouble())
        } else if (yTop + entry.height > height) {
            setScroll((entry.scrolledY + entry.height - height).toDouble())
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        updateScroll(-verticalAmount * (listHeight / children.size / 2))
        return true
    }

    override fun mouseClicked(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        val entry = getEntryAtPosition(pMouseX.toInt(), pMouseY.toInt())
        if (entry != null) {
            return entry.mouseClicked(pMouseX, pMouseY, pButton)
        }
        return false
    }

    override fun mouseReleased(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        return children.any {
            it.mouseReleased(pMouseX, pMouseY, pButton)
        } || super.mouseReleased(pMouseX, pMouseY, pButton)
    }

    fun getRowTop(entry: Entry): Int {
        if (entry !in children) return -1
        return y + entry.scrolledY - scrollAmount.toInt()
    }

    fun getLeft(): Int {
        return x
    }

    override fun defaultButtonNarrationText(pNarrationElementOutput: NarrationElementOutput) {}
    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    abstract inner class Entry(var height: Int): GuiEventListener {
        var scrolledY: Int = 0

        private var widgetFocused = false

        override fun setFocused(focused: Boolean) {
            this.widgetFocused = focused
        }

        override fun isFocused(): Boolean = widgetFocused

        abstract fun render(guiGraphics: GuiGraphics, index: Int, x: Int, y: Int, mouseX: Int, mouseY: Int, partialTick: Float)

        fun getRowTop(): Int {
            return getRowTop(this)
        }

        fun getRowLeft(): Int {
            return getLeft()
        }
    }

    inner class EmptySeparator(height: Int): Entry(height) {
        override fun render(guiGraphics: GuiGraphics, index: Int, x: Int, y: Int, mouseX: Int, mouseY: Int, partialTick: Float) {}
    }
}