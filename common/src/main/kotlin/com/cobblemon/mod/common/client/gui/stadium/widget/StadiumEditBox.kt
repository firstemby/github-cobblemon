/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.stadium.StadiumSkin
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StadiumEditBox(private val textFont: Font, x: Int, y: Int, width: Int, label: Component, private val range: IntRange? = null) : EditBox(textFont, x, y, width, 22, label) {
    private var textStart = 0
    private var anchor = 0
    private var ink = StadiumSkin.INK
    private var placeholder: Component? = null
    init {
        setBordered(false)
        if (range != null) setFilter { text -> text.all { it in '0'..'9' } }
    }

    override fun setFocused(focused: Boolean) {
        if (!focused && range != null) value.toIntOrNull()?.let { value = it.coerceIn(range).toString() }
        super.setFocused(focused)
    }

    override fun getInnerWidth() = (width - 14).coerceAtLeast(1)

    override fun setHighlightPos(position: Int) {
        super.setHighlightPos(position)
        anchor = position.coerceIn(0, value.length)
    }

    override fun setTextColor(color: Int) { super.setTextColor(color); ink = color or 0xFF000000.toInt() }
    override fun setHint(hint: Component) { super.setHint(hint); placeholder = hint }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        StadiumSkin.panel(graphics, StadiumSkin.Surface.CONTROL, x, y, width, height, 3)
        if (isFocused) graphics.renderOutline(x, y, width, height, StadiumSkin.FOCUS)
        textStart = textStart.coerceIn(0, value.length)
        if (cursorPosition < textStart) textStart = cursorPosition
        if (textFont.width(value.substring(textStart, cursorPosition)) > innerWidth) {
            textStart = cursorPosition - textFont.plainSubstrByWidth(value.substring(0, cursorPosition), innerWidth, true).length
        }
        if (textFont.width(value) <= innerWidth) textStart = 0
        val shown = textFont.plainSubstrByWidth(value.substring(textStart), innerWidth)
        fun position(index: Int) = x + 7 + textFont.width(shown.take((index - textStart).coerceIn(0, shown.length)))
        val scissorLeft = x + 6
        graphics.enableScissor(scissorLeft, y + 3, (x + width - 5).coerceAtLeast(scissorLeft + 1), y + height - 3)
        try {
            if (isFocused && anchor != cursorPosition) {
                graphics.fill(position(minOf(anchor, cursorPosition)), y + 5, position(maxOf(anchor, cursorPosition)), y + 17, 0xFFADCFC4.toInt())
            }
            graphics.drawString(textFont, shown, x + 7, y + 7, ink, false)
            if (value.isEmpty() && !isFocused) placeholder?.let {
                graphics.drawString(textFont, textFont.plainSubstrByWidth(it.string, innerWidth), x + 7, y + 7, StadiumSkin.MUTED, false)
            }
            if (isFocused && System.currentTimeMillis() / 500 % 2 == 0L) {
                graphics.fill(position(cursorPosition), y + 5, position(cursorPosition) + 1, y + 17, ink)
            }
        } finally {
            graphics.disableScissor()
        }
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        val target = (mouseX - x - 7).toInt().coerceAtLeast(0)
        moveCursorTo(textStart + textFont.plainSubstrByWidth(value.substring(textStart), target).length, Screen.hasShiftDown())
    }
}
