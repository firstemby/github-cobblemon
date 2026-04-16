/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.renderer

import com.mojang.blaze3d.buffers.GpuBufferSlice
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import org.joml.Matrix4f

class CachedOrthoProjectionMatrixBuffer(
    name: String,
    private val minDepth: Float,
    private val maxDepth: Float,
    private val flipY: Boolean
) : AutoCloseable {
    private val projectionMatrixBuffer = ProjectionMatrixBuffer(name)
    private val matrix = Matrix4f()

    fun getBuffer(width: Float, height: Float): GpuBufferSlice {
        matrix.identity()
        if (flipY) {
            matrix.setOrtho(0.0F, width, height, 0.0F, minDepth, maxDepth)
        } else {
            matrix.setOrtho(0.0F, width, 0.0F, height, minDepth, maxDepth)
        }
        return projectionMatrixBuffer.getBuffer(matrix)
    }

    override fun close() {
        projectionMatrixBuffer.close()
    }
}
