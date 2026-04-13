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
import net.minecraft.client.gui.GuiGraphics

object ProgressBarRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val value = widget.definition.getProperty("value")?.resolveDouble(runtime) ?: 0.0
        val max = widget.definition.getProperty("max")?.resolveDouble(runtime) ?: 100.0
        val bgColor = widget.definition.getProperty("background_color")?.resolveInt(runtime) ?: 0xFF333333.toInt()
        val barColor = widget.definition.getProperty("color")?.resolveInt(runtime) ?: 0xFF00FF00.toInt()
        val direction = widget.definition.getStringProperty("direction") ?: "left_to_right"

        val x = widget.absoluteX.toInt()
        val y = widget.absoluteY.toInt()
        val w = widget.computedWidth.toInt()
        val h = widget.computedHeight.toInt()
        val fraction = if (max > 0) (value / max).coerceIn(0.0, 1.0) else 0.0

        guiGraphics.fill(x, y, x + w, y + h, bgColor)
        when (direction) {
            "left_to_right" -> guiGraphics.fill(x, y, x + (w * fraction).toInt(), y + h, barColor)
            "right_to_left" -> guiGraphics.fill(x + w - (w * fraction).toInt(), y, x + w, y + h, barColor)
            "bottom_to_top" -> guiGraphics.fill(x, y + h - (h * fraction).toInt(), x + w, y + h, barColor)
            "top_to_bottom" -> guiGraphics.fill(x, y, x + w, y + (h * fraction).toInt(), barColor)
        }
    }
}
