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
import com.cobblemon.mod.common.client.render.withStandardBlend
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
        val texture = ResourceLocation.tryParse(textureStr) ?: return
        if (!widget.absoluteX.isFinite() || !widget.absoluteY.isFinite()) return
        val width = pixelSize(widget.computedWidth) ?: return
        val height = pixelSize(widget.computedHeight) ?: return
        val uvProperty = widget.definition.getProperty("uv")
        val uv = uvProperty?.raw as? List<*>
        if (uvProperty != null && uv == null) return
        val sizeProperty = widget.definition.getProperty("texture_size")
        val textureSize = sizeProperty?.raw as? List<*>
        if (sizeProperty != null && textureSize == null) return
        val texW = pixelSize(component(textureSize, 0, if (uv == null) width.toFloat() else 256f)) ?: return
        val texH = pixelSize(component(textureSize, 1, if (uv == null) height.toFloat() else 256f)) ?: return
        val u = component(uv, 0, 0f) ?: return
        val v = component(uv, 1, 0f) ?: return
        val uvW = pixelSize(component(uv, 2, if (uv == null) texW.toFloat() else width.toFloat())) ?: return
        val uvH = pixelSize(component(uv, 3, if (uv == null) texH.toFloat() else height.toFloat())) ?: return
        withStandardBlend {
            guiGraphics.blit(
                texture,
                widget.absoluteX.toInt(), widget.absoluteY.toInt(), width, height,
                u, v, uvW, uvH, texW, texH
            )
        }
    }

    private fun component(values: List<*>?, index: Int, default: Float): Float? {
        if (values == null || index >= values.size) return default
        return (values[index] as? Number)?.toFloat()?.takeIf { it.isFinite() }
    }

    private fun pixelSize(value: Float?): Int? = value?.takeIf {
        it.isFinite() && it >= 1f && it.toDouble() <= Int.MAX_VALUE.toDouble()
    }?.toInt()
}
