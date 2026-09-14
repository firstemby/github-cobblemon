/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.cinematic

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CinematicWorldRendererFrustumTest {

    @Test
    fun `vertical forward vector receives a finite orthogonal up vector`() {
        val basis = frustumBasis(Vector3f(0f, 1f, 0f), Vector3f(0f, 1f, 0f))

        assertNotNull(basis)
        val (forward, up) = basis!!
        assertEquals(0f, forward.dot(up), 1e-6f)
        assertEquals(1f, up.length(), 1e-6f)
    }
}
