/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.client.render.camera.FeedLensMask
import com.cobblemon.mod.common.client.render.camera.FeedLensMask.Rectangle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeedLensMaskTest {
    @Test
    fun `wide and narrow aspect crops mask the appropriate edges`() {
        assertEquals(listOf(Rectangle(0, 0, 1920, 140), Rectangle(0, 940, 1920, 140)),
            FeedLensMask.rectangles(1920, 1080, Lens(aspectCrop = 2.4f)))
        assertEquals(listOf(Rectangle(0, 0, 420, 1080), Rectangle(1500, 0, 420, 1080)),
            FeedLensMask.rectangles(1920, 1080, Lens(aspectCrop = 1f)))
    }

    @Test
    fun `letterbox combines with aspect crop and covers odd sized frames at one half`() {
        val masks = FeedLensMask.rectangles(1920, 1080, Lens(aspectCrop = 1f, letterbox = 0.25f))
        assertEquals(listOf(Rectangle(0, 0, 1920, 270), Rectangle(0, 810, 1920, 270),
            Rectangle(0, 270, 420, 540), Rectangle(1500, 270, 420, 540)), masks)
        assertEquals(listOf(Rectangle(0, 0, 101, 99)), FeedLensMask.rectangles(101, 99, Lens(letterbox = 0.5f)))
        assertTrue(FeedLensMask.rectangles(1920, 1080, null).isEmpty())
        assertTrue(FeedLensMask.rectangles(1920, 1080, Lens()).isEmpty())
        assertTrue(FeedLensMask.rectangles(0, 0, Lens(letterbox = 0.5f)).isEmpty())
    }

    @Test
    fun `mask retains the captured viewport aspect when its texture dimensions differ`() {
        assertEquals(listOf(Rectangle(0, 0, 1920, 180), Rectangle(0, 900, 1920, 180)),
            FeedLensMask.rectangles(1920, 1080, Lens(aspectCrop = 2f), sourceAspect = 4.0 / 3.0))
    }
}
