/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewPlaybackTest {
    @Test
    fun `pause holds clock and does not sample state again`() {
        val clock = PreviewPlayback(Beat("main", duration = 8f))
        clock.seek(3f, true)
        val samples = mutableListOf<Float>()
        clock.evaluate({}, samples::add)
        assertEquals(61, samples.size)
        repeat(10) { clock.advance(0.05f); assertFalse(clock.evaluate({}, samples::add)) }
        assertEquals(3f, clock.time)
    }

    @Test
    fun `scrub direction and history do not affect stateful samples`() {
        val clock = PreviewPlayback(Beat("main", duration = 8f))
        var state = 0.0
        fun sample(time: Float): Double {
            clock.seek(time, true)
            clock.evaluate({ state = 0.0 }, { state += (it * 5.0 - state) / 11.0 })
            return state
        }
        val expected = sample(3f)
        sample(7f)
        assertEquals(expected, sample(3f), 0.0)
        sample(0.1f)
        assertEquals(expected, sample(3f), 0.0)
    }

    @Test
    fun `incremental playback matches replay and is independent of tick partition`() {
        fun run(deltas: List<Float>): Pair<Float, Double> {
            val clock = PreviewPlayback(Beat("main", duration = 8f))
            var state = 0.0
            clock.seek(0f, false)
            deltas.forEach { delta ->
                clock.advance(delta)
                clock.evaluate({ state = 0.0 }, { state += (it * 5.0 - state) / 11.0 })
            }
            return clock.time to state
        }
        assertEquals(run(List(60) { 0.05f }), run(List(30) { 0.1f }))
    }

    @Test
    fun `non-looping preview samples exact endpoint and pauses`() {
        val clock = PreviewPlayback(Beat("main", duration = 0.123f))
        clock.seek(0f, false)
        clock.advance(0.5f)
        val samples = mutableListOf<Float>()
        clock.evaluate({}, samples::add)
        assertEquals(listOf(0f, 0.05f, 0.1f, 0.123f), samples)
        assertTrue(clock.paused)
        assertFalse(clock.evaluate({}, samples::add))
    }

    @Test
    fun `loop restarts replay and speed affects the single clock`() {
        val clock = PreviewPlayback(Beat("main", duration = 1f, speed = 2f, isLooping = true))
        clock.seek(0.8f, false)
        clock.advance(0.2f)
        assertEquals(0.2f, clock.time, 0.0001f)
        var resets = 0
        clock.evaluate({ resets++ }, {})
        assertEquals(1, resets)
        assertFalse(clock.paused)
    }

    @Test
    fun `invalid times and excessive replay work are rejected`() {
        val clock = PreviewPlayback(Beat("main"))
        assertThrows(IllegalArgumentException::class.java) { clock.seek(Float.NaN, true) }
        assertThrows(IllegalArgumentException::class.java) { PreviewPlayback(Beat("main", duration = 601f)) }
        clock.seek(-5f, true)
        assertEquals(0f, clock.time)
        clock.seek(700f, true)
        assertEquals(600f, clock.time)
    }

    @Test
    fun `zero duration stays finite even when looping`() {
        val clock = PreviewPlayback(Beat("main", duration = 0f, isLooping = true))
        clock.seek(0f, false)
        clock.advance(0.1f)
        assertEquals(0f, clock.time)
        assertTrue(clock.paused)
    }
}
