/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewportCameraModeTest {
    @Test
    fun `default viewport camera is free-fly`() {
        assertEquals(ViewportCameraMode.FREE_FLY, EditorPlayState().viewportCameraMode)
    }

    @Test
    fun `toggle switches free-fly and scene cam`() {
        val s = EditorPlayState()
        s.toggleViewportCamera()
        assertEquals(ViewportCameraMode.SCENE_CAM, s.viewportCameraMode)
        s.toggleViewportCamera()
        assertEquals(ViewportCameraMode.FREE_FLY, s.viewportCameraMode)
    }

    @Test
    fun `entering Play does not auto-switch the viewport camera`() {
        val s = EditorPlayState()
        s.togglePlay()
        assertEquals(ViewportCameraMode.FREE_FLY, s.viewportCameraMode)
    }
}
