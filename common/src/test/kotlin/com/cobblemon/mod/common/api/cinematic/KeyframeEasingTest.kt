/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.tween.EasingRegistry
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyframeEasingTest {
    @Test
    fun `destination easing overrides the default and endpoints hold without startup registration`() {
        val source = KeyframePosition(listOf(
            KeyframePosition.Kf(2f, Vec3(20.0, 0.0, 0.0), "step"),
            KeyframePosition.Kf(0f, Vec3.ZERO),
            KeyframePosition.Kf(1f, Vec3(10.0, 0.0, 0.0))
        ), defaultEasing = "ease_in_quad")
        for ((time, expected) in listOf(-1f to 0.0, 0f to 0.0, 0.5f to 2.5, 1f to 10.0, 1.5f to 10.0, 2f to 20.0, 3f to 20.0)) {
            assertEquals(expected, source.evaluate(time).x, 0.00001, "position at $time")
        }
    }

    @Test
    fun `overshoot easing is preserved between fixed endpoints`() {
        val source = KeyframePosition(listOf(KeyframePosition.Kf(0f, Vec3.ZERO),
            KeyframePosition.Kf(1f, Vec3(10.0, 0.0, 0.0))), defaultEasing = "ease_out_back")
        assertTrue(source.evaluate(0.5f).x > 10.0)
        assertEquals(Vec3.ZERO, source.evaluate(-1f))
        assertEquals(Vec3(10.0, 0.0, 0.0), source.evaluate(2f))
    }

    @Test
    fun `canonical easing curves are available and finite across their entire input domain`() {
        val names = EasingRegistry.getRegisteredNames()
        assertEquals(32, names.size)
        for (name in names) {
            val ease = EasingRegistry.getEasingFunction(name)
            assertEquals(0f, ease(0f), 0.00001f, name)
            assertEquals(1f, ease(1f), 0.00001f, name)
            for (sample in 0..100) assertTrue(ease(sample / 100f).isFinite(), "$name at $sample")
        }
        assertEquals(0.75f, EasingRegistry.getEasingFunction("ease_out_quad")(0.5f))
        assertEquals(0.5f, EasingRegistry.getEasingFunction("easeOutQuad")(0.5f))
    }
}
