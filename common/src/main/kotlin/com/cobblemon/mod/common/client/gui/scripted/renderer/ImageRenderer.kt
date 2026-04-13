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
import net.minecraft.resources.ResourceLocation

object ImageRenderer : WidgetRenderer {
    override fun render(
        widget: WidgetInstance,
        guiGraphics: GuiGraphics,
        runtime: MoLangRuntime,
        mouseX: Float,
        mouseY: Float,
        delta: Float
    ) {
        val textureStr = widget.definition.getProperty("texture")?.resolveString(runtime) ?: return
        val texture = ResourceLocation.parse(textureStr)
        val uv = widget.definition.getProperty("uv")
        val textureSize = widget.definition.getProperty("texture_size")

        if (uv != null && uv.raw is List<*>) {
            val uvList = uv.raw as List<*>
            val u = (uvList.getOrNull(0) as? Number)?.toFloat() ?: 0f
            val v = (uvList.getOrNull(1) as? Number)?.toFloat() ?: 0f
            val uvW = (uvList.getOrNull(2) as? Number)?.toFloat() ?: widget.computedWidth
            val uvH = (uvList.getOrNull(3) as? Number)?.toFloat() ?: widget.computedHeight
            val texW = if (textureSize != null && textureSize.raw is List<*>)
                ((textureSize.raw as List<*>).getOrNull(0) as? Number)?.toInt() ?: 256
            else 256
            val texH = if (textureSize != null && textureSize.raw is List<*>)
                ((textureSize.raw as List<*>).getOrNull(1) as? Number)?.toInt() ?: 256
            else 256
            guiGraphics.blit(
                texture,
                widget.absoluteX.toInt(), widget.absoluteY.toInt(),
                u, v,
                widget.computedWidth.toInt(), widget.computedHeight.toInt(),
                texW, texH
            )
        } else {
            guiGraphics.blit(
                texture,
                widget.absoluteX.toInt(), widget.absoluteY.toInt(),
                0f, 0f,
                widget.computedWidth.toInt(), widget.computedHeight.toInt(),
                widget.computedWidth.toInt(), widget.computedHeight.toInt()
            )
        }
    }
}
