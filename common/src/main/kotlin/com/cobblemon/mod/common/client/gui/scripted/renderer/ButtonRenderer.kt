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

object ButtonRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val enabled = widget.definition.getProperty("enabled")?.resolveBoolean(runtime) ?: true
        val baseColor = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFF555555.toInt()

        val color = when {
            !enabled -> 0xFF333333.toInt()
            widget.pressed -> 0xFF333333.toInt()
            widget.hovered -> widget.definition.hoverOverrides["color"]?.resolveInt(runtime) ?: 0xFF777777.toInt()
            else -> baseColor
        }

        guiGraphics.fill(
            widget.absoluteX.toInt(), widget.absoluteY.toInt(),
            (widget.absoluteX + widget.computedWidth).toInt(),
            (widget.absoluteY + widget.computedHeight).toInt(),
            color
        )

        val text = widget.definition.getProperty("text")?.resolveString(runtime) ?: ""
        if (text.isNotEmpty()) {
            val font = Minecraft.getInstance().font
            val textX = widget.absoluteX + widget.computedWidth / 2f - font.width(text) / 2f
            val textY = widget.absoluteY + widget.computedHeight / 2f - font.lineHeight / 2f
            val textColor = if (enabled) 0xFFFFFF else 0xAAAAAA
            guiGraphics.drawString(font, text, textX.toInt(), textY.toInt(), textColor, true)
        }
    }
}
