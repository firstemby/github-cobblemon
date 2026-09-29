/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor.layout

import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import org.joml.Matrix4f
import java.util.UUID

class DiagonalSplitLayout(
    val primaryCameraId: UUID,
    val secondaryCameraId: UUID,
    val splitAngleDegrees: Float = 30f,
    val gapWidth: Float = 2f
) : CompositorLayout {
    init {
        require(splitAngleDegrees.isFinite())
        require(gapWidth.isFinite() && gapWidth >= 0f)
    }

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(primaryCameraId, secondaryCameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        val regions = SplitLayoutGeometry.diagonal(width, height, splitAngleDegrees, gapWidth)
        SplitLayoutRenderer.render(matrix, width, height, primaryCameraId, secondaryCameraId, regions)
    }

    companion object {
        const val TYPE = "diagonal_split"
    }
}
