/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui.renderer

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molangui.MolangUiParameters
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object NumberInputRenderer : WidgetRenderer {
    private const val BUTTON_WIDTH = 20

    private data class Values(val min: Int, val max: Int, val step: Int, val current: Int)
    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int) {
        val buttonWidth = minOf(BUTTON_WIDTH, width / 3)
        val right get() = x + width
        val bottom get() = y + height

        fun direction(mouseX: Float, mouseY: Float): Int = when {
            !mouseX.isFinite() || !mouseY.isFinite() || mouseY < y || mouseY >= bottom -> 0
            mouseX >= x && mouseX < x + buttonWidth -> -1
            mouseX >= right - buttonWidth && mouseX < right -> 1
            else -> 0
        }
    }

    override fun render(widget: WidgetInstance, guiGraphics: GuiGraphics, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, delta: Float) {
        val bounds = bounds(widget) ?: return
        val values = values(widget, runtime) ?: return
        val font = Minecraft.getInstance().font
        val textColor = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFFFFFF
        val buttonColor = widget.definition.getProperty("button_color")?.resolveInt(runtime) ?: 0xFF555555.toInt()
        val hoverColor = 0xFF777777.toInt()
        val hovered = if (widget.enabled) bounds.direction(mouseX, mouseY) else 0
        guiGraphics.fill(bounds.x, bounds.y, bounds.x + bounds.buttonWidth, bounds.bottom,
            if (hovered == -1 && values.current > values.min) hoverColor else buttonColor)
        guiGraphics.fill(bounds.right - bounds.buttonWidth, bounds.y, bounds.right, bounds.bottom,
            if (hovered == 1 && values.current < values.max) hoverColor else buttonColor)

        fun drawText(text: String, left: Int, width: Int) {
            val textWidth = font.width(text)
            if (textWidth > width || font.lineHeight > bounds.height) return
            guiGraphics.drawString(font, text, left + (width - textWidth) / 2,
                bounds.y + (bounds.height - font.lineHeight) / 2, textColor, false)
        }
        drawText("?", bounds.x, bounds.buttonWidth)
        drawText(values.current.toString(), bounds.x + bounds.buttonWidth, bounds.width - 2 * bounds.buttonWidth)
        drawText("+", bounds.right - bounds.buttonWidth, bounds.buttonWidth)
    }

    fun handleClick(widget: WidgetInstance, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, onChange: (Int) -> Unit): Boolean {
        if (!widget.visible || !widget.enabled) return false
        val direction = bounds(widget)?.direction(mouseX, mouseY) ?: return false
        if (direction == 0) return false
        val values = values(widget, runtime) ?: return false
        val variable = widget.definition.getStringProperty("variable") ?: return false
        val name = try { MolangUiParameters.requireVariableName(variable) } catch (_: IllegalArgumentException) { return false }
        val newValue = (values.current.toLong() + direction * values.step.toLong())
            .coerceIn(values.min.toLong(), values.max.toLong()).toInt()
        if (newValue != values.current) {
            runtime.environment.setSimpleVariable(name, DoubleValue(newValue.toDouble()))
            onChange(newValue)
        }
        return true
    }

    private fun values(widget: WidgetInstance, runtime: MoLangRuntime): Values? {
        fun integer(key: String, default: Double): Int? {
            val value = widget.definition.getProperty(key)?.resolveDouble(runtime) ?: default
            return value.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
        }
        val min = integer("min", 1.0) ?: return null
        val max = integer("max", 99.0) ?: return null
        val step = integer("step", 1.0) ?: return null
        val variable = widget.definition.getStringProperty("variable")?.removePrefix("v.")
        val boundValue = variable?.let { runtime.environment.variable.map[it]?.asDouble() } ?: min.toDouble()
        val current = integer("value", boundValue) ?: return null
        if (min > max || step <= 0) return null
        return Values(min, max, step, current)
    }

    private fun bounds(widget: WidgetInstance): Bounds? {
        if (!widget.absoluteX.isFinite() || !widget.absoluteY.isFinite() ||
            !widget.computedWidth.isFinite() || !widget.computedHeight.isFinite()) return null
        val x = widget.absoluteX.toDouble()
        val y = widget.absoluteY.toDouble()
        val width = widget.computedWidth.toDouble()
        val height = widget.computedHeight.toDouble()
        if (width < 3 || height < 1 || width > Int.MAX_VALUE || height > Int.MAX_VALUE ||
            x < Int.MIN_VALUE || y < Int.MIN_VALUE || x + width > Int.MAX_VALUE || y + height > Int.MAX_VALUE) return null
        return Bounds(x.toInt(), y.toInt(), width.toInt(), height.toInt())
    }
}
