/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import org.joml.Matrix4f
import java.util.UUID

interface CompositorLayout {
    val type: String

    fun requiredFeeds(): Set<UUID>

    /**
     * @param matrix Orthographic projection matrix (0,0 top-left to width,height bottom-right)
     */
    fun render(matrix: Matrix4f, width: Float, height: Float)
}
