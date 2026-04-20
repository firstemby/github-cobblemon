/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class StadiumToggleWidget(
    x: Int, y: Int,
    private var value: Boolean,
    private val onChange: (Boolean) -> Unit
) : AbstractWidget(x, y, TRACK_WIDTH, TRACK_HEIGHT, Component.empty()), CobblemonRenderable {

    companion object {
        private const val TRACK_WIDTH = 24
        private const val TRACK_HEIGHT = 12
        private const val KNOB_SIZE = 10
        private const val TRACK_ON_COLOR = 0xFF4455AA.toInt()
        private const val TRACK_OFF_COLOR = 0xFF3A3A5C.toInt()
        private const val KNOB_COLOR = 0xFFFFFFFF.toInt()
    }

    fun getValue(): Boolean = value

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Track
        val trackColor = if (value) TRACK_ON_COLOR else TRACK_OFF_COLOR
        context.fill(x, y, x + TRACK_WIDTH, y + TRACK_HEIGHT, trackColor)
        context.renderOutline(x, y, TRACK_WIDTH, TRACK_HEIGHT, 0xFF2A2A44.toInt())

        // Knob
        val knobX = if (value) x + TRACK_WIDTH - KNOB_SIZE - 1 else x + 1
        context.fill(knobX, y + 1, knobX + KNOB_SIZE, y + 1 + KNOB_SIZE, KNOB_COLOR)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        value = !value
        onChange(value)
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
