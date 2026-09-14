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

class SceneSelectionTest {

    @Test
    fun `default selection is SceneRoot`() {
        val mgr = SceneSelectionManager()
        assertEquals(SceneSelection.SceneRoot, mgr.selection)
    }

    @Test
    fun `getter helpers return null for the wrong selection kind`() {
        val mgr = SceneSelectionManager()
        mgr.select(SceneSelection.ShotSelected("wide_arena"))
        assertEquals("wide_arena", mgr.getSelectedShotId())
        assertNull(mgr.getSelectedActorId())
        assertNull(mgr.getSelectedBeatId())
    }

    @Test
    fun `clear returns selection to SceneRoot`() {
        val mgr = SceneSelectionManager()
        mgr.select(SceneSelection.BeatSelected("main"))
        mgr.clear()
        assertEquals(SceneSelection.SceneRoot, mgr.selection)
        assertNull(mgr.getSelectedBeatId())
    }

    @Test
    fun `actor cut and shot selection retain the editing beat`() {
        val mgr = SceneSelectionManager()
        mgr.select(SceneSelection.BeatActorSelected("intro", "hero"))
        assertEquals("intro", mgr.getSelectedBeatId())
        mgr.select(SceneSelection.ShotSelected("closeup"))
        assertEquals("intro", mgr.getSelectedBeatId())
        mgr.select(SceneSelection.CutSelected("outro", "main", 0))
        mgr.select(SceneSelection.ActorSelected("hero"))
        assertEquals("outro", mgr.getSelectedBeatId())
    }

    @Test
    fun `removing a preceding list item preserves the selected feed and transition`() {
        val mgr = SceneSelectionManager()
        mgr.select(SceneSelection.FeedSelected(2))
        mgr.onFeedRemoved(0)
        assertEquals(SceneSelection.FeedSelected(1), mgr.selection)

        mgr.select(SceneSelection.TransitionSelected(2))
        mgr.onTransitionRemoved(1)
        assertEquals(SceneSelection.TransitionSelected(1), mgr.selection)
    }

    @Test
    fun `removing the selected list item clears selection`() {
        val mgr = SceneSelectionManager()
        mgr.select(SceneSelection.FeedSelected(1))
        mgr.onFeedRemoved(1)
        assertEquals(SceneSelection.SceneRoot, mgr.selection)

        mgr.select(SceneSelection.TransitionSelected(0))
        mgr.onTransitionRemoved(0)
        assertEquals(SceneSelection.SceneRoot, mgr.selection)
    }
}
