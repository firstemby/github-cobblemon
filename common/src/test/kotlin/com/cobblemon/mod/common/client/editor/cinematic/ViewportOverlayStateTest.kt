/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ViewportOverlayStateTest {

    @Test
    fun `defaults match design spec`() {
        val s = ViewportOverlayState()
        assertTrue(s.cameraPathsAndFrustums)
        assertTrue(s.actors)
        assertTrue(s.keyframesAndGizmos)
        assertFalse(s.showAllShots)
        assertTrue(s.focusLines)
        assertTrue(s.originAndCompass)
        assertFalse(s.gridOverlay)
    }

    @Test
    fun `toggle flips a single flag`() {
        val s = ViewportOverlayState()
        s.toggleGridOverlay()
        assertTrue(s.gridOverlay)
        s.toggleGridOverlay()
        assertFalse(s.gridOverlay)
    }
}
