/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.client.gui.stadium.StadiumSkin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class StadiumCheckbox(x: Int, y: Int, width: Int, label: String, initial: Boolean, change: (Boolean) -> Unit) :
    StadiumButton(x, y, width, 22, Component.literal(label), onPress = {}) {
    var checked = initial
        private set
    private val changeValue = change

    override fun onPress() {
        checked = !checked
        changeValue(checked)
    }

    override fun createNarrationMessage(): MutableComponent = Component.translatable(
        if (checked) "cobblemon.stadium.checkbox.checked" else "cobblemon.stadium.checkbox.unchecked", message
    )

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        StadiumSkin.panel(graphics, StadiumSkin.Surface.DISPLAY, x, y, width, height)
        StadiumSkin.panel(graphics, if (checked) StadiumSkin.Surface.SELECTED else StadiumSkin.Surface.CONTROL, x + 4, y + 5, 12, 12)
        val font = Minecraft.getInstance().font
        if (checked) graphics.drawString(font, "✓", x + 6, y + 7, StadiumSkin.TEXT, false)
        graphics.drawString(font, font.plainSubstrByWidth(message.string, (width - 26).coerceAtLeast(0)), x + 22, y + 7, StadiumSkin.INK, false)
        if (isHoveredOrFocused) graphics.renderOutline(x, y, width, height, StadiumSkin.FOCUS)
    }
}
