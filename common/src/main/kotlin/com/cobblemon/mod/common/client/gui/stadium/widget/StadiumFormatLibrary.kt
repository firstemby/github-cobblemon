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

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.client.gui.stadium.StadiumSkin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class StadiumFormatLibrary(x: Int, y: Int, width: Int, height: Int,
    formats: Map<String, StadiumFormatConfig>, private val selected: String,
    private val choose: (String) -> Unit
) : AbstractWidget(x, y, width, height, Component.translatable("cobblemon.stadium.library.your_formats")) {
    private val entries = formats.toList()
    private val capacity get() = (height / 37).coerceAtLeast(1)
    private var first = (entries.indexOfFirst { it.first == selected } - capacity + 1).coerceAtLeast(0)

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        entries.drop(first).take(capacity).forEachIndexed { index, (id, config) ->
            val top = y + index * 37
            StadiumSkin.panel(graphics, if (id == selected) StadiumSkin.Surface.CONTROL else StadiumSkin.Surface.CASE, x, top, width, 34)
            if (id == selected) graphics.fill(x, top + 3, x + 2, top + 31, StadiumSkin.FOCUS)
            graphics.drawString(font, font.plainSubstrByWidth(config.displayName.string, width - 12), x + 6, top + 5, StadiumSkin.INK, false)
            graphics.drawString(font, I18n.get("cobblemon.stadium.layout.${config.battleTypeName}"), x + 6, top + 19, StadiumSkin.MUTED, false)
        }
        if (isFocused) graphics.renderOutline(x, y, width, height, StadiumSkin.FOCUS)
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        val row = ((mouseY - y) / 37).toInt()
        if (row !in 0 until capacity || mouseY >= y + row * 37 + 34) return
        entries.getOrNull(first + row)?.let { choose(it.first) }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        first = (first - verticalAmount.toInt()).coerceIn(0, (entries.size - capacity).coerceAtLeast(0))
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode != GLFW.GLFW_KEY_DOWN && keyCode != GLFW.GLFW_KEY_UP) return false
        val index = entries.indexOfFirst { it.first == selected } + if (keyCode == GLFW.GLFW_KEY_DOWN) 1 else -1
        entries.getOrNull(index)?.let { choose(it.first) }
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
}
