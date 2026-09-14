/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GizmoHandlesTest {

    private val origin = Vector2f(100f, 100f)
    private val tips = mapOf(
        GizmoAxis.X to Vector2f(180f, 100f),
        GizmoAxis.Y to Vector2f(100f, 20f),
        GizmoAxis.Z to Vector2f(160f, 160f)
    )

    @Test
    fun `hit picks the arm under the cursor and nothing when the cursor is away`() {
        assertEquals(GizmoAxis.X, GizmoHandles.hit(Vector2f(140f, 103f), origin, tips))
        assertEquals(GizmoAxis.Y, GizmoHandles.hit(Vector2f(102f, 60f), origin, tips))
        assertNull(GizmoHandles.hit(Vector2f(400f, 400f), origin, tips))
    }

    @Test
    fun `an arm pointing at the camera collapses on screen and stops being hittable`() {
        val collapsed = mapOf(GizmoAxis.X to Vector2f(105f, 100f))
        assertNull(GizmoHandles.hit(Vector2f(103f, 100f), origin, collapsed))
    }

    @Test
    fun `axisDelta scales a drag along the arm and ignores one across it`() {
        val tip = Vector2f(200f, 100f)
        assertEquals(
            1f,
            GizmoHandles.axisDelta(Vector2f(100f, 100f), Vector2f(150f, 100f), origin, tip, armWorldLength = 2f),
            1e-4f
        )
        assertEquals(
            0f,
            GizmoHandles.axisDelta(Vector2f(100f, 100f), Vector2f(100f, 150f), origin, tip, armWorldLength = 2f),
            1e-4f
        )
    }
}
