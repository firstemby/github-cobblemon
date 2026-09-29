/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import net.minecraft.util.Mth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class CameraInterpolationUtilsTest {
    @Test
    fun `rotation crosses the shortest wrap and respects per-frame speed`() {
        val from = CameraRotation(170f, -170f)
        val to = CameraRotation(-170f, 170f)
        assertEquals(CameraRotation(-180f, -180f), CameraInterpolationUtils.lerpRotation(from, to, 0.5f))
        assertEquals(CameraRotation(175f, -175f), CameraInterpolationUtils.lerpRotationClamped(from, to, 1f, 0.05f, 100f))
    }

    @Test
    fun `large finite angles are normalized before their difference is computed`() {
        val from = CameraRotation(Float.MAX_VALUE, -Float.MAX_VALUE)
        val to = CameraRotation(-Float.MAX_VALUE, Float.MAX_VALUE)
        val normalizedFrom = CameraRotation(Mth.wrapDegrees(from.yaw), Mth.wrapDegrees(from.pitch))
        val normalizedTo = CameraRotation(Mth.wrapDegrees(to.yaw), Mth.wrapDegrees(to.pitch))
        val actual = CameraInterpolationUtils.lerpRotation(from, to, 0.5f)
        assertTrue(actual.yaw.isFinite() && actual.pitch.isFinite())
        assertEquals(CameraInterpolationUtils.lerpRotation(normalizedFrom, normalizedTo, 0.5f), actual)
        assertEquals(CameraInterpolationUtils.lerpRotationClamped(normalizedFrom, normalizedTo, 1f, 0.05f, 100f),
            CameraInterpolationUtils.lerpRotationClamped(from, to, 1f, 0.05f, 100f))
    }
}
