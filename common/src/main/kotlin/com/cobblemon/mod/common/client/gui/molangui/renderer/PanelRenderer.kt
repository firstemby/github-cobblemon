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
import net.minecraft.client.gui.GuiGraphics

object PanelRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val color = widget.definition.getProperty("color")
        if (color != null) {
            guiGraphics.fill(
                widget.absoluteX.toInt(), widget.absoluteY.toInt(),
                (widget.absoluteX + widget.computedWidth).toInt(),
                (widget.absoluteY + widget.computedHeight).toInt(),
                color.resolveInt(runtime)
            )
        }
        ImageRenderer.render(widget, guiGraphics, runtime, mouseX, mouseY, delta)
    }
}
