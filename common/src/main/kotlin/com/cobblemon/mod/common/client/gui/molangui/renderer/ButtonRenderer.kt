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
        val enabled = widget.enabled
        val baseColor = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFF555555.toInt()

        val color = when {
            !enabled -> 0xFF333333.toInt()
            widget.hovered -> widget.definition.hoverOverrides["color"]?.resolveInt(runtime) ?: 0xFF777777.toInt()
            else -> baseColor
        }

        guiGraphics.fill(
            widget.absoluteX.toInt(), widget.absoluteY.toInt(),
            (widget.absoluteX + widget.computedWidth).toInt(),
            (widget.absoluteY + widget.computedHeight).toInt(),
            color
        )

        val text = widget.definition.getProperty("text")?.resolveString(runtime) ?: return
        val contentWidth = (widget.computedWidth.toInt() - 4).coerceAtLeast(0)
        if (text.isEmpty() || contentWidth == 0) return
        val font = Minecraft.getInstance().font
        if (widget.computedHeight < font.lineHeight + 2) return
        val displayed = if (font.width(text) <= contentWidth) text else {
            val suffix = "?"
            val suffixWidth = font.width(suffix)
            if (suffixWidth > contentWidth) return
            font.plainSubstrByWidth(text, contentWidth - suffixWidth) + suffix
        }
        val textX = widget.absoluteX + (widget.computedWidth - font.width(displayed)) / 2f
        val textY = widget.absoluteY + (widget.computedHeight - font.lineHeight) / 2f
        val textColor = if (enabled) 0xFFFFFF else 0xAAAAAA
        guiGraphics.enableScissor(widget.absoluteX.toInt(), widget.absoluteY.toInt(),
            (widget.absoluteX + widget.computedWidth).toInt(), (widget.absoluteY + widget.computedHeight).toInt())
        try {
            guiGraphics.drawString(font, displayed, textX.toInt(), textY.toInt(), textColor, true)
        } finally {
            guiGraphics.disableScissor()
        }
    }
}
