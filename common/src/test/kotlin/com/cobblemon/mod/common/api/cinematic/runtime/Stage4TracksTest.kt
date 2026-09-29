/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Stage4TracksTest {

    @Test
    fun `SplineMovement clamps to endpoints outside range`() {
        val track = SplineMovement(
            controlPoints = listOf(Vec3(0.0, 0.0, 0.0), Vec3(10.0, 0.0, 0.0)),
            durationSecs = 1f
        )
        val start = track.evaluate(0f)
        val end = track.evaluate(1f)
        assertEquals(0.0, start.x, 0.01)
        assertEquals(10.0, end.x, 0.01)
        assertEquals(10.0, track.evaluate(5f).x, 0.01)
    }

    @Test
    fun `SplineMovement midpoint lies between endpoints`() {
        val track = SplineMovement(
            controlPoints = listOf(Vec3(0.0, 0.0, 0.0), Vec3(10.0, 0.0, 0.0)),
            durationSecs = 2f
        )
        val mid = track.evaluate(1f)
        assertTrue(mid.x > 0.0 && mid.x < 10.0, "Midpoint ${mid.x} should be between 0 and 10")
    }
}
