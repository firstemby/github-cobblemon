/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

internal class RenderBlendState private constructor(
    private val enabled: Boolean,
    private val sourceRgb: Int,
    private val destinationRgb: Int,
    private val sourceAlpha: Int,
    private val destinationAlpha: Int,
    private val color: FloatArray
) {
    fun restore() {
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
        restoreColor(color)
        if (enabled) RenderSystem.enableBlend() else RenderSystem.disableBlend()
    }

    companion object {
        fun capture() = RenderBlendState(
            GL11.glIsEnabled(GL11.GL_BLEND),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), readColor()
        )

        fun readColor() = FloatArray(4).also { GL11.glGetFloatv(GL_BLEND_COLOR, it) }

        fun restoreColor(color: FloatArray) = GL14.glBlendColor(color[0], color[1], color[2], color[3])
    }
}

internal inline fun withStandardBlend(draw: () -> Unit) {
    val state = RenderBlendState.capture()
    try {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        draw()
    } finally {
        state.restore()
    }
}
