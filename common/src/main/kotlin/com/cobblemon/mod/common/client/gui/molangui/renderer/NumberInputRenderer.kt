/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object NumberInputRenderer : WidgetRenderer {
    private const val BUTTON_WIDTH = 20

    override fun render(widget: WidgetInstance, guiGraphics: GuiGraphics, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, delta: Float) {
        val font = Minecraft.getInstance().font
        val value = widget.definition.getProperty("value")?.resolveInt(runtime) ?: 0
        val textColor = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFFFFFF
        val buttonColor = widget.definition.getProperty("button_color")?.resolveInt(runtime) ?: 0xFF555555.toInt()
        val hoverColor = 0xFF777777.toInt()

        val x = widget.absoluteX.toInt()
        val y = widget.absoluteY.toInt()
        val w = widget.computedWidth.toInt()
        val h = widget.computedHeight.toInt()

        // [−] button
        val minusBtnHovered = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + h
        guiGraphics.fill(x, y, x + BUTTON_WIDTH, y + h, if (minusBtnHovered) hoverColor else buttonColor)
        val minusX = x + BUTTON_WIDTH / 2 - font.width("−") / 2
        val textY = y + h / 2 - font.lineHeight / 2
        guiGraphics.drawString(font, "−", minusX, textY, textColor, false)

        // Value display
        val valueStr = value.toString()
        val valueX = x + BUTTON_WIDTH + (w - 2 * BUTTON_WIDTH) / 2 - font.width(valueStr) / 2
        guiGraphics.drawString(font, valueStr, valueX, textY, textColor, false)

        // [+] button
        val plusX = x + w - BUTTON_WIDTH
        val plusBtnHovered = mouseX >= plusX && mouseX < plusX + BUTTON_WIDTH && mouseY >= y && mouseY < y + h
        guiGraphics.fill(plusX, y, plusX + BUTTON_WIDTH, y + h, if (plusBtnHovered) hoverColor else buttonColor)
        val plusTextX = plusX + BUTTON_WIDTH / 2 - font.width("+") / 2
        guiGraphics.drawString(font, "+", plusTextX, textY, textColor, false)
    }

    fun handleClick(widget: WidgetInstance, runtime: MoLangRuntime, mouseX: Float, mouseY: Float): Boolean {
        val x = widget.absoluteX
        val y = widget.absoluteY
        val w = widget.computedWidth
        val h = widget.computedHeight
        if (mouseY < y || mouseY >= y + h) return false

        val min = widget.definition.getProperty("min")?.resolveInt(runtime) ?: 1
        val max = widget.definition.getProperty("max")?.resolveInt(runtime) ?: 99
        val step = widget.definition.getProperty("step")?.resolveInt(runtime) ?: 1
        val currentValue = widget.definition.getProperty("value")?.resolveInt(runtime) ?: min
        val variableName = widget.definition.getStringProperty("variable") ?: return false

        val newValue = when {
            mouseX >= x && mouseX < x + BUTTON_WIDTH -> (currentValue - step).coerceAtLeast(min)
            mouseX >= x + w - BUTTON_WIDTH && mouseX < x + w -> (currentValue + step).coerceAtMost(max)
            else -> return false
        }

        runtime.environment.setSimpleVariable(variableName, DoubleValue(newValue.toDouble()))
        return true
    }
}
