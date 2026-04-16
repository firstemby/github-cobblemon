/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.npc.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.util.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import kotlin.math.floor

class SimpleNPCTextInputWidget(
    getter: () -> String,
    val texture: Identifier? = null,
    private val setter: (String) -> Unit,
    posX: Int,
    posY: Int,
    width: Int,
    height: Int,
    maxLength: Int = 100,
    val shadow: Boolean = true,
    val wrap: Boolean = false
) : EditBox(
    Minecraft.getInstance().font,
    posX + TEXT_PADDING,
    posY,
    width,
    height,
    "input".text()
), CobblemonRenderable {
    companion object {
        const val TEXT_PADDING = 4
    }

    var focusedTime: Long = 0

    init {
        setMaxLength(maxLength)
        isFocused = true
        focusedTime = Util.getMillis()
        value = getter()
        setResponder { setter(it) }
        setFocused(false)
        isBordered = false
    }

    override fun setFocused(focused: Boolean) {
        if (focused) focusedTime = Util.getMillis()
        super.setFocused(focused)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        val clickedState = super.mouseClicked(mouseButtonEvent, doubleClick)

        if (wrap) {
            val splitLines = Minecraft.getInstance().font.splitter.splitLines(value, width - 8, Style.EMPTY)
            val clickedLineIndex = floor((mouseY - y)/11).toInt()

            if (clickedLineIndex >= 0 && splitLines.size > clickedLineIndex) {
                val lineAtIndex = splitLines.get(clickedLineIndex)
                var lineSubstring = ""
                for (char in lineAtIndex.string) {
                    lineSubstring += char
                    val startToLineWidth = Minecraft.getInstance().font.width(lineSubstring.text())
                    if ((mouseX - x) <= startToLineWidth) break
                }
                var unwrappedLine = ""
                splitLines.forEachIndexed { index, line ->
                    if (index == clickedLineIndex) {
                        unwrappedLine += lineSubstring
                    } else if (index < clickedLineIndex) {
                        unwrappedLine += line.string
                    }
                }

                this.moveCursorTo(maxOf(0, unwrappedLine.length - 1), false)
            }
        }

        return clickedState
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (texture != null) {
            blitk(
                matrixStack = context.pose(),
                x = x,
                y = y,
                width = width,
                height = height,
                texture = texture
            )
        }

        if (wrap) {
            val showCursor = isFocused && ((Util.getMillis() - this.focusedTime) / 300L % 2L == 0L)
            val input = (if (isFocused) "${value}${if ((cursorPosition == value.length) && showCursor) "_" else ""}" else value)

            var cursorLine = ""
            var cursorLineIndex = -1
            var cursorLinePos = 0
            var accumlatedLength = 0
            val wrappedLines = Minecraft.getInstance().font.splitter.splitLines(input, width - (TEXT_PADDING * 2), Style.EMPTY)

            for ((index, line) in wrappedLines.withIndex()) {
                accumlatedLength += line.string.length
                if (cursorLineIndex < 0 && accumlatedLength > cursorPosition) {
                    cursorLineIndex = index
                    cursorLine = line.string
                    cursorLinePos = if (cursorLineIndex > 0) cursorPosition - (accumlatedLength - line.string.length) else cursorPosition
                }
                drawScaledText(
                    context = context,
                    text = line.string.text(),
                    x = x,
                    y = y + index * 10 + 1,
                    colour = 0xDDDDDD,
                    shadow = true
                )
            }

            if (showCursor && !value.isEmpty() && cursorPosition != value.length) {
                val startToCursorWidth = Minecraft.getInstance().font.width((cursorLine.substring(0, cursorLinePos).text()))
                context.fill(
                    x + startToCursorWidth - 1,
                    y + cursorLineIndex * 10 + 1,
                    x + startToCursorWidth,
                    y + (cursorLineIndex * 10 + 1) + 9,
                    -3092272
                )
            }
        } else {
            super.extractWidgetRenderState(context, mouseX, mouseY, delta)
        }
    }
}
