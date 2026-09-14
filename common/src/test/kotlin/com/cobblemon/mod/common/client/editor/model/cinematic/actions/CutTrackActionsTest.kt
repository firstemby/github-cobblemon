/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CutTrackActionsTest {
    @Test fun `adding at an earlier playhead inserts chronologically and survives undo redo`() {
        val scene = sceneWithBeatAndFeed()
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(0f, "a")))
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(8f, "c")))
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(4f, "b")))
        val entries = scene.beats["main"]!!.cuts["primary"]!!.entries
        assertEquals(listOf(0f, 4f, 8f), entries.map { it.time })
        scene.undo()
        assertEquals(listOf("a", "c"), entries.map { it.shotId })
        scene.redo()
        assertEquals(listOf("a", "b", "c"), entries.map { it.shotId })
        scene.execute(SplitCutAt("main", "primary", 6f))
        assertEquals(listOf("a", "b", "b", "c"), entries.map { it.shotId })
    }


    private fun sceneWithBeatAndFeed(): EditorScene {
        val scene = EditorScene(id = "test:scene", startBeatId = "main")
        val beat = EditorBeat(id = "main", duration = 10f)
        beat.cuts["primary"] = EditorCutTrack()
        scene.beats["main"] = beat
        return scene
    }

    @Test
    fun `adding a feed to one beat creates its track and supports undo redo`() {
        val scene = EditorScene(startBeatId = "intro")
        val intro = EditorBeat(id = "intro")
        val main = EditorBeat(id = "main")
        scene.beats["intro"] = intro
        scene.beats["main"] = main
        val entry = EditorCutEntry(time = 0f, shotId = "trainer")

        scene.execute(AddCutEntry("intro", "side_a", entry))
        assertEquals(listOf(entry), intro.cuts["side_a"]?.entries)
        assertTrue(main.cuts.isEmpty())

        scene.undo()
        assertTrue(intro.cuts.isEmpty())

        scene.redo()
        assertEquals(listOf(entry), intro.cuts["side_a"]?.entries)
        assertTrue(main.cuts.isEmpty())
    }

    @Test
    fun `AddCutEntry appends and undo removes`() {
        val scene = sceneWithBeatAndFeed()
        val entry = EditorCutEntry(time = 0f, shotId = "intro", transition = CutTransition.Instant)

        scene.execute(AddCutEntry("main", "primary", entry))
        assertEquals(1, scene.beats["main"]!!.cuts["primary"]!!.entries.size)

        scene.undo()
        assertEquals(0, scene.beats["main"]!!.cuts["primary"]!!.entries.size)
    }

    @Test
    fun `ChangeCutEntry time round-trips through undo`() {
        val scene = sceneWithBeatAndFeed()
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(0f, "a", CutTransition.Instant)))
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(5f, "b", CutTransition.Instant)))

        scene.execute(ChangeCutEntry.time("main", "primary", entryIndex = 1, oldTime = 5f, newTime = 3f))
        assertEquals(3f, scene.beats["main"]!!.cuts["primary"]!!.entries[1].time)

        scene.undo()
        assertEquals(5f, scene.beats["main"]!!.cuts["primary"]!!.entries[1].time)
    }

    @Test
    fun `RemoveCutEntry drops the entry and undo restores it at the same index`() {
        val scene = sceneWithBeatAndFeed()
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(0f, "a", CutTransition.Instant)))
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(5f, "b", CutTransition.Instant)))

        val removed = scene.beats["main"]!!.cuts["primary"]!!.entries[0]
        scene.execute(RemoveCutEntry("main", "primary", entryIndex = 0, existing = removed))
        assertEquals(1, scene.beats["main"]!!.cuts["primary"]!!.entries.size)
        assertEquals("b", scene.beats["main"]!!.cuts["primary"]!!.entries[0].shotId)

        scene.undo()
        assertEquals(2, scene.beats["main"]!!.cuts["primary"]!!.entries.size)
        assertEquals("a", scene.beats["main"]!!.cuts["primary"]!!.entries[0].shotId)
    }

    @Test
    fun `SplitCutAt duplicates the owning entry at the new time`() {
        val scene = sceneWithBeatAndFeed()
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(0f, "shotA", CutTransition.Instant)))
        scene.execute(AddCutEntry("main", "primary", EditorCutEntry(10f, "shotB", CutTransition.Instant)))

        scene.execute(SplitCutAt("main", "primary", playheadSeconds = 4f))

        val entries = scene.beats["main"]!!.cuts["primary"]!!.entries
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.time == 4f && it.shotId == "shotA" })

        scene.undo()
        assertEquals(2, scene.beats["main"]!!.cuts["primary"]!!.entries.size)
    }
}
