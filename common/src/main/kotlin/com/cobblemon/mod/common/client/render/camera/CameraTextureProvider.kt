/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import java.util.UUID

object CameraTextureProvider {

    /** Returns -1 if unavailable. */
    @JvmStatic
    fun getFeedTextureId(cameraId: UUID): Int {
        return CameraFeedHandler.getTextureId(cameraId) ?: -1
    }

    /** Returns -1 if unavailable. */
    @JvmStatic
    fun getCompositorTextureId(compositorId: UUID): Int {
        val compositor = CompositorManager.getCompositor(compositorId) ?: return -1
        return compositor.getTextureId()
    }

    @JvmStatic
    fun isFeedReady(cameraId: UUID): Boolean {
        return CameraFeedHandler.getTextureId(cameraId) != null
    }

    @JvmStatic
    fun isCompositorReady(compositorId: UUID): Boolean {
        return CompositorManager.getCompositor(compositorId) != null
    }
}
