/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.client.render.camera.compositor.layout.DiagonalSplitLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.PictureInPictureLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SideBySideLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SplitLayoutGeometry
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SplitLayoutGeometry.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.abs

class SplitLayoutGeometryTest {
    @Test
    fun `diagonal feeds and gap partition the viewport at every angle`() {
        for ((width, height) in listOf(1920f to 1080f, 300f to 900f, 1f to 1f)) {
            for (angle in listOf(-90f, -89.99f, -45f, 0f, 30f, 89.99f, 90f, 135f, 180f, Float.MAX_VALUE)) {
                for (gap in listOf(0f, 2f, 100f, Float.MAX_VALUE)) {
                    val regions = SplitLayoutGeometry.diagonal(width, height, angle, gap)
                    val polygons = listOf(regions.primary, regions.secondary, regions.gap)
                    assertEquals(width.toDouble() * height, polygons.sumOf(::area), width.toDouble() * height * 1e-9)
                    for (polygon in polygons) {
                        assertTrue(polygon.all { it.x.isFinite() && it.y.isFinite() && it.x >= -1e-9 && it.x <= width + 1e-9 && it.y >= -1e-9 && it.y <= height + 1e-9 })
                        val uv = SplitLayoutGeometry.textureCoordinates(polygon, width, height)
                        assertTrue(uv.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
                    }
                }
            }
        }
    }

    @Test
    fun `straight splits center both feeds and keep oversized gaps inside viewport`() {
        for (vertical in listOf(false, true)) {
            for (ratio in listOf(0f, 0.01f, 0.5f, 0.99f, 1f)) {
                for (gap in listOf(0f, 2f, Float.MAX_VALUE)) {
                    val regions = SplitLayoutGeometry.straight(800f, 600f, vertical, ratio, gap)
                    assertEquals(480000.0, listOf(regions.primary, regions.secondary, regions.gap).sumOf(::area), 1e-7)
                    for (polygon in listOf(regions.primary, regions.secondary)) {
                        if (polygon.isEmpty()) continue
                        val uv = SplitLayoutGeometry.textureCoordinates(polygon, 800f, 600f)
                        assertEquals(1.0, uv.minOf { it.x } + uv.maxOf { it.x }, 1e-9)
                        assertEquals(1.0, uv.minOf { it.y } + uv.maxOf { it.y }, 1e-9)
                    }
                }
            }
        }
    }

    @Test
    fun `layouts reject nonfinite negative and out of viewport parameters`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertThrows(IllegalArgumentException::class.java) { SideBySideLayout(first, second, gapWidth = value) }
            assertThrows(IllegalArgumentException::class.java) { DiagonalSplitLayout(first, second, gapWidth = value) }
            assertThrows(IllegalArgumentException::class.java) { PictureInPictureLayout(first, second, borderWidth = value) }
        }
        assertThrows(IllegalArgumentException::class.java) { DiagonalSplitLayout(first, second, splitAngleDegrees = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SideBySideLayout(first, second, splitRatio = 1.1f) }
        assertThrows(IllegalArgumentException::class.java) { PictureInPictureLayout(first, second, pipWidth = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { PictureInPictureLayout(first, second, pipX = 0.9f, pipWidth = 0.2f) }
    }

    private fun area(polygon: List<Point>): Double {
        if (polygon.size < 3) return 0.0
        return abs(polygon.indices.sumOf { index ->
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            a.x * b.y - b.x * a.y
        }) / 2.0
    }
}
