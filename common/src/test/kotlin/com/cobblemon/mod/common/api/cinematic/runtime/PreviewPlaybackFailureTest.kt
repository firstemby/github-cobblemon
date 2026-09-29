/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PreviewPlaybackFailureTest {
    @Test
    fun `a failed sample is retried`() {
        val playback = PreviewPlayback(Beat("main", duration = 1f))
        playback.seek(0f, true)
        assertThrows(IllegalStateException::class.java) { playback.evaluate({}, { error("failed") }) }
        val samples = mutableListOf<Float>()
        playback.evaluate({}, samples::add)
        assertEquals(listOf(0f), samples)
    }
}
