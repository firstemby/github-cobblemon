/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShotCompositionTest {

    @Test
    fun `minimal shot with position and rotation constructs`() {
        val shot = Shot(id = "s", position = StubPos, rotation = StubRot)
        assertEquals("s", shot.id)
        assertNull(shot.fov)
        assertNull(shot.roll)
        assertNull(shot.lens)
        assertTrue(shot.modifiers.isEmpty())
    }

    @Test
    fun `blank shot id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Shot(id = "", position = StubPos, rotation = StubRot)
        }
    }

    @Test
    fun `lens letterbox bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lens(letterbox = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Lens(letterbox = 0.51f)
        }
        Lens(letterbox = 0f)
        Lens(letterbox = 0.5f)
    }

    @Test
    fun `lens aspect crop must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lens(aspectCrop = -1.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Lens(aspectCrop = 0.0f)
        }
        Lens(aspectCrop = 1.78f)
    }
}

private object StubPos : PositionSource() { override val type = "stub" }
private object StubRot : RotationSource() { override val type = "stub" }
