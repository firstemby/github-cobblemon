/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.generic

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class BedrockEntityTransformTest {
    @Test fun `drone lens follows camera yaw and pitch`() {
        for (yaw in listOf(-180f, -90f, 0f, 90f, 180f)) {
            for (pitch in listOf(-90f, -30f, 0f, 30f, 90f)) {
                val stack = PoseStack()
                BedrockEntityTransform.apply(stack, yaw, 1f, pitch)
                val direction = stack.last().pose().transformDirection(Vector3f(0f, 0f, -1f))
                val y = Math.toRadians(yaw.toDouble())
                val p = Math.toRadians(pitch.toDouble())
                assertEquals((-sin(y) * cos(p)).toFloat(), direction.x, 0.00001f)
                assertEquals(-sin(p).toFloat(), direction.y, 0.00001f)
                assertEquals((cos(y) * cos(p)).toFloat(), direction.z, 0.00001f)
                stack.translate(0.0, 4.65 / 16.0, 2.75 / 16.0)
                val lens = stack.last().pose().transformPosition(Vector3f(0f, -4.65f / 16f, -2.75f / 16f))
                assertEquals(0f, lens.length(), 0.00001f)
            }
        }
    }

    @Test fun `outer cube faces retain outward winding at every yaw and preview scale`() {
        for (yaw in listOf(0f, 45f, 90f, 180f, 270f, 359f)) {
            for (scale in listOf(0.25f, 1f, 2.5f)) {
                val stack = PoseStack()
                BedrockEntityTransform.apply(stack, yaw, scale)
                val pose = stack.last().pose()
                val a = pose.transformPosition(Vector3f(-1f, -1f, -1f))
                val b = pose.transformPosition(Vector3f(-1f, 1f, -1f))
                val c = pose.transformPosition(Vector3f(1f, 1f, -1f))
                val faceNormal = b.sub(a).cross(c.sub(a)).normalize()
                val outward = stack.last().normal().transform(Vector3f(0f, 0f, -1f)).normalize()
                assertTrue(faceNormal.dot(outward) > 0.999f, "Inside-out face at yaw=$yaw, scale=$scale")
                assertTrue(pose.determinant() > 0f)
            }
        }
    }

    @Test fun `model faces the entity yaw with its feet anchored and its head upright`() {
        for (yaw in listOf(-180f, -90f, 0f, 45f, 90f, 180f)) {
            val stack = PoseStack()
            BedrockEntityTransform.apply(stack, yaw, 2f)
            val pose = stack.last().pose()
            assertEquals(Vector3f(), pose.transformPosition(Vector3f()))
            assertEquals(4f, pose.transformPosition(Vector3f(0f, -2f, 0f)).y, 0.00001f)
            val front = pose.transformDirection(Vector3f(0f, 0f, -1f)).normalize()
            val radians = Math.toRadians(yaw.toDouble())
            assertEquals(-sin(radians).toFloat(), front.x, 0.00001f)
            assertEquals(cos(radians).toFloat(), front.z, 0.00001f)
        }
    }
}
