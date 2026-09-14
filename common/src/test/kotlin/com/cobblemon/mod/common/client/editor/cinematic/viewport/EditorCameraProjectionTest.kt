/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EditorCameraProjectionTest {

    private val camera = Vec3(10.0, 64.0, -5.0)
    private val viewProj: Matrix4f =
        Matrix4f().perspective(Math.toRadians(70.0).toFloat(), 16f / 9f, 0.05f, 1000f)

    @Test
    fun `a point straight ahead lands in the middle of the viewport rect`() {
        val screen = EditorCameraProjection.project(
            viewProj, camera,
            x = 10.0, y = 64.0, z = -15.0,
            rectX = 100f, rectY = 50f, rectW = 800f, rectH = 400f
        )
        assertNotNull(screen)
        assertEquals(500f, screen!!.x, 0.5f)
        assertEquals(250f, screen.y, 0.5f)
    }

    @Test
    fun `a point behind the camera is not projectable`() {
        val screen = EditorCameraProjection.project(
            viewProj, camera,
            x = 10.0, y = 64.0, z = 5.0,
            rectX = 100f, rectY = 50f, rectW = 800f, rectH = 400f
        )
        assertNull(screen)
    }

    @Test
    fun `screen Y grows downward, opposite to world Y`() {
        val above = EditorCameraProjection.project(
            viewProj, camera, 10.0, 66.0, -15.0, 100f, 50f, 800f, 400f
        )!!
        assertEquals(true, above.y < 250f, "a point above the camera should sit above centre")
    }

    @Test
    fun `non-finite projection inputs are rejected`() {
        assertNull(EditorCameraProjection.project(
            viewProj, camera, Double.NaN, 64.0, -15.0, 100f, 50f, 800f, 400f
        ))
        assertNull(EditorCameraProjection.project(
            viewProj, camera, 10.0, 64.0, -15.0, 100f, 50f, Float.POSITIVE_INFINITY, 400f
        ))
    }
}
