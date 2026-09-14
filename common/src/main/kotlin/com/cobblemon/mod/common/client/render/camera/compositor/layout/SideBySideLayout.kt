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

class SideBySideLayout(
    val leftCameraId: UUID,
    val rightCameraId: UUID,
    val vertical: Boolean = false,
    val splitRatio: Float = 0.5f,
    val gapWidth: Float = 2f
) : CompositorLayout {
    init {
        require(splitRatio.isFinite() && splitRatio in 0f..1f)
        require(gapWidth.isFinite() && gapWidth >= 0f)
    }

    override val type: String = TYPE

    override fun requiredFeeds(): Set<UUID> = setOf(leftCameraId, rightCameraId)

    override fun render(matrix: Matrix4f, width: Float, height: Float) {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return
        val regions = SplitLayoutGeometry.straight(width, height, vertical, splitRatio, gapWidth)
        SplitLayoutRenderer.render(matrix, width, height, leftCameraId, rightCameraId, regions)
    }

    companion object {
        const val TYPE = "side_by_side"
    }
}
