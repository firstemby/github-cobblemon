/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActorDirectionCarryOverTest {

    private fun pos(x: Double, y: Double, z: Double) = PositionExpression(
        CoordinateValue.Static(x),
        CoordinateValue.Static(y),
        CoordinateValue.Static(z)
    )

    @Test
    fun `start pose requires visible != false`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActorDirection(
                visible = false,
                startPose = StartPose(pos(0.0, 0.0, 0.0), CameraRotation(0f, 0f))
            )
        }
    }

    @Test
    fun `start pose with visible = true is allowed`() {
        val direction = ActorDirection(
            visible = true,
            startPose = StartPose(pos(1.0, 2.0, 3.0), CameraRotation(45f, -10f)),
            startPoseMode = StartPoseMode.TELEPORT
        )
        assertEquals(StartPoseMode.TELEPORT, direction.startPoseMode)
    }

    @Test
    fun `start pose with visible = null (carry over) is allowed`() {
        val direction = ActorDirection(
            startPose = StartPose(pos(0.0, 0.0, 0.0), CameraRotation(0f, 0f))
        )
        assertNull(direction.visible)
        assertNotNull(direction.startPose)
    }
}
