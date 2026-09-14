/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.stadium.StadiumSkin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

open class StadiumButton(x: Int, y: Int, width: Int, height: Int, label: Component, var accent: Boolean = false, onPress: OnPress) :
    Button(x, y, width, height, label, onPress, DEFAULT_NARRATION) {
    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val surface = if (active && (accent || isHoveredOrFocused)) StadiumSkin.Surface.SELECTED else StadiumSkin.Surface.CONTROL
        StadiumSkin.panel(graphics, surface, x, y, width, height, 3)
        if (isFocused) graphics.renderOutline(x, y, width, height, StadiumSkin.FOCUS)
        val font = Minecraft.getInstance().font
        tooltip = if (font.width(message) > width - 12) Tooltip.create(message) else null
        val text = font.plainSubstrByWidth(message.string, (width - 12).coerceAtLeast(1))
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2,
            if (!active) StadiumSkin.MUTED else if (surface == StadiumSkin.Surface.SELECTED) StadiumSkin.TEXT else StadiumSkin.INK, false)
    }
}
