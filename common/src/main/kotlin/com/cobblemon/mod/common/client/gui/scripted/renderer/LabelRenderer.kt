/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted.renderer

import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetInstance
import com.bedrockk.molang.runtime.MoLangRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object LabelRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val font = Minecraft.getInstance().font
        val text = widget.definition.getProperty("text")?.resolveString(runtime) ?: ""
        val color = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFFFFFF
        val shadow = widget.definition.getProperty("shadow")?.resolveBoolean(runtime) ?: false
        val alignment = widget.definition.getStringProperty("alignment") ?: "left"

        val x = when (alignment) {
            "center" -> widget.absoluteX + widget.computedWidth / 2f - font.width(text) / 2f
            "right" -> widget.absoluteX + widget.computedWidth - font.width(text)
            else -> widget.absoluteX
        }

        guiGraphics.drawString(font, text, x.toInt(), widget.absoluteY.toInt(), color, shadow)
    }
}
