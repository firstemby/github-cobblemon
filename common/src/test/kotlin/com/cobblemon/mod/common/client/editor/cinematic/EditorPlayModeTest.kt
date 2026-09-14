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

class EditorPlayModeTest {

    @Test
    fun `defaults to EDIT`() {
        val state = EditorPlayState()
        assertEquals(EditorPlayMode.EDIT, state.mode)
    }

    @Test
    fun `setMode fires listener on change only`() {
        val state = EditorPlayState()
        var fires = 0
        state.onModeChanged { fires++ }
        state.setMode(EditorPlayMode.PLAY)
        state.setMode(EditorPlayMode.PLAY)
        state.setMode(EditorPlayMode.TAKEOVER)
        state.setMode(EditorPlayMode.EDIT)
        assertEquals(3, fires)
    }

    @Test
    fun `togglePlay flips between EDIT and PLAY`() {
        val state = EditorPlayState()
        state.togglePlay()
        assertEquals(EditorPlayMode.PLAY, state.mode)
        state.togglePlay()
        assertEquals(EditorPlayMode.EDIT, state.mode)
    }

    @Test
    fun `togglePlay from TAKEOVER exits to EDIT first`() {
        val state = EditorPlayState()
        state.setMode(EditorPlayMode.TAKEOVER)
        state.togglePlay()
        assertEquals(EditorPlayMode.EDIT, state.mode)
    }

    @Test
    fun `exitTakeover only acts when in TAKEOVER`() {
        val state = EditorPlayState()
        state.setMode(EditorPlayMode.PLAY)
        val changed = state.exitTakeover()
        assertFalse(changed)
        assertEquals(EditorPlayMode.PLAY, state.mode)

        state.setMode(EditorPlayMode.TAKEOVER)
        val changed2 = state.exitTakeover()
        assertTrue(changed2)
        assertEquals(EditorPlayMode.EDIT, state.mode)
    }

    @Test
    fun `togglePlay from TAKEOVER then again reaches PLAY`() {
        val state = EditorPlayState()
        state.setMode(EditorPlayMode.TAKEOVER)
        state.togglePlay()
        state.togglePlay()
        assertEquals(EditorPlayMode.PLAY, state.mode)
    }
}
