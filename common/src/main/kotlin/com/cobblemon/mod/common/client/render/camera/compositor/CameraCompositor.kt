/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.Cobblemon
import com.mojang.blaze3d.pipeline.TextureTarget
import java.util.UUID

class CameraCompositor(
    val compositorId: UUID,
    var layout: CompositorLayout,
    outputWidth: Int,
    outputHeight: Int
) {
    // Minimized windows report zero dimensions, which produce invalid targets and NaN divider geometry.
    var outputWidth: Int = outputWidth.coerceAtLeast(1)
        private set
    var outputHeight: Int = outputHeight.coerceAtLeast(1)
        private set

    private var renderTarget: TextureTarget = TextureTarget(this.outputWidth, this.outputHeight, true, false)

    fun getRenderTarget(): TextureTarget = renderTarget

    fun composite() {
        CompositorRenderState.render(renderTarget, outputWidth, outputHeight) { matrix ->
            layout.render(matrix, outputWidth.toFloat(), outputHeight.toFloat())
        }
    }

    fun resize(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (w != outputWidth || h != outputHeight) {
            val replacement = TextureTarget(w, h, true, false)
            val previous = renderTarget
            renderTarget = replacement
            outputWidth = w
            outputHeight = h
            runCatching { previous.destroyBuffers() }
                .onFailure { Cobblemon.LOGGER.warn("Could not dispose replaced compositor target {}", compositorId, it) }
        }
    }

    fun dispose() {
        renderTarget.destroyBuffers()
    }
}
