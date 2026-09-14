/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.client.editor.cinematic.panel.TimelinePlayhead
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TimelinePanelMathTest {

    @Test
    fun `playhead time clamps to beat duration for non-looping beat`() {
        val ph = TimelinePlayhead()
        ph.setBeatDuration(2.5f, looping = false)
        ph.setTime(3.0f)
        assertEquals(2.5f, ph.time, 1e-4f)
    }

    @Test
    fun `playhead time wraps around for looping beat`() {
        val ph = TimelinePlayhead()
        ph.setBeatDuration(2.5f, looping = true)
        ph.setTime(6.2f)
        assertEquals(1.2f, ph.time, 1e-4f)
    }

    @Test
    fun `step advances by tickSeconds and clamps`() {
        val ph = TimelinePlayhead()
        ph.setBeatDuration(1.0f, looping = false)
        ph.setTime(0.9f)
        ph.step(0.2f)
        assertEquals(1.0f, ph.time, 1e-4f)
    }

    @Test
    fun `playhead normalized returns 0 for zero duration`() {
        val ph = TimelinePlayhead()
        ph.setBeatDuration(0f, looping = false)
        ph.setTime(1f)
        assertEquals(0f, ph.normalized)
    }
}
