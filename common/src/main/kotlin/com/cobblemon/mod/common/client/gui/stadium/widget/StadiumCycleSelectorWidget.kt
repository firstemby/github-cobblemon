/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class StadiumCycleSelectorWidget<T>(
    x: Int, y: Int,
    width: Int,
    private val options: List<T>,
    private val displayMapper: (T) -> String,
    initialIndex: Int = 0,
    private val onChange: (T) -> Unit
) : AbstractWidget(x, y, width, 14, Component.empty()), CobblemonRenderable {

    private var selectedIndex = initialIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    val selectedValue: T get() = options[selectedIndex]

    private val arrowWidth = 12

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val leftHovered = mouseX in x..(x + arrowWidth) && mouseY in y..(y + height)
        val rightHovered = mouseX in (x + width - arrowWidth)..(x + width) && mouseY in y..(y + height)

        // Left arrow
        drawScaledText(
            context = context,
            text = Component.literal("\u25C0"),
            x = x + arrowWidth / 2,
            y = y + 3,
            colour = if (leftHovered) 0x88AAFF else 0x6688FF,
            centered = true,
            shadow = true
        )

        // Value
        if (options.isNotEmpty()) {
            drawScaledText(
                context = context,
                text = Component.literal(displayMapper(selectedValue)),
                x = x + width / 2,
                y = y + 3,
                colour = 0xFFFFFF,
                centered = true,
                shadow = true
            )
        }

        // Right arrow
        drawScaledText(
            context = context,
            text = Component.literal("\u25B6"),
            x = x + width - arrowWidth / 2,
            y = y + 3,
            colour = if (rightHovered) 0x88AAFF else 0x6688FF,
            centered = true,
            shadow = true
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY) || options.isEmpty()) return false

        if (mouseX < x + arrowWidth) {
            selectedIndex = (selectedIndex - 1 + options.size) % options.size
            onChange(selectedValue)
            return true
        } else if (mouseX > x + width - arrowWidth) {
            selectedIndex = (selectedIndex + 1) % options.size
            onChange(selectedValue)
            return true
        }
        return false
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
