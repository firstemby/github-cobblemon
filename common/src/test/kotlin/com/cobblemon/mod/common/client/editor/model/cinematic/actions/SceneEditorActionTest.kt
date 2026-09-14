/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.editor.model.cinematic.ChangeSceneProperty
import com.cobblemon.mod.common.client.editor.model.cinematic.CompoundSceneAction
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeatTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SceneEditorActionTest {

    private lateinit var scene: EditorScene

    @BeforeEach
    fun freshScene() {
        scene = EditorScene.createDefault()
    }

    private fun stubShotFor(id: String) = EditorShot(
        id = id,
        positionData = JsonObject().also { it.addProperty("type", "stub") },
        rotationData = JsonObject().also { it.addProperty("type", "stub") }
    )


    @Test
    fun `AddActor adds and undo removes`() {
        val action = AddActor(EditorActor(id = "hero", kind = ActorKind.PlayerTakeover))
        action.execute(scene)
        assertEquals(ActorKind.PlayerTakeover, scene.actors["hero"]?.kind)
        action.undo(scene)
        assertNull(scene.actors["hero"])
    }

    @Test
    fun `RemoveActor removes and undo re-adds`() {
        scene.actors["hero"] = EditorActor(id = "hero", kind = ActorKind.PlayerTakeover)
        val action = RemoveActor("hero", scene.actors["hero"]!!)
        action.execute(scene)
        assertNull(scene.actors["hero"])
        action.undo(scene)
        assertEquals(ActorKind.PlayerTakeover, scene.actors["hero"]?.kind)
    }

    @Test
    fun `ChangeActorKind changes kind and undo reverts`() {
        scene.actors["a"] = EditorActor(id = "a", kind = ActorKind.RealEntity)
        val action = ChangeActorKind("a", ActorKind.RealEntity, ActorKind.SynthesizedPokemon)
        action.execute(scene)
        assertEquals(ActorKind.SynthesizedPokemon, scene.actors["a"]?.kind)
        action.undo(scene)
        assertEquals(ActorKind.RealEntity, scene.actors["a"]?.kind)
    }


    @Test
    fun `AddFeed appends and undo removes`() {
        val feed = EditorFeed(id = "primary", kind = FeedKind.Fullscreen)
        val action = AddFeed(feed)
        action.execute(scene)
        assertEquals(1, scene.feeds.size)
        action.undo(scene)
        assertEquals(0, scene.feeds.size)
    }

    @Test
    fun `RemoveFeed preserves index on undo`() {
        val f0 = EditorFeed(id = "primary", kind = FeedKind.Fullscreen)
        val f1 = EditorFeed(id = "pip", kind = FeedKind.Pip(FeedKind.Corner.TOP_RIGHT, 0.3f))
        scene.feeds.addAll(listOf(f0, f1))
        val action = RemoveFeed(index = 0, feed = f0)
        action.execute(scene)
        assertEquals(listOf(f1), scene.feeds.toList())
        action.undo(scene)
        assertEquals(listOf(f0, f1), scene.feeds.toList())
    }


    @Test
    fun `AddShot adds and undo removes`() {
        val shot = stubShotFor("wide")
        AddShot(shot).execute(scene)
        assertEquals(shot, scene.shots["wide"])
        AddShot(shot).undo(scene)
        assertNull(scene.shots["wide"])
    }

    @Test
    fun `RemoveShot removes and undo re-adds`() {
        val shot = stubShotFor("wide")
        scene.shots["wide"] = shot
        val action = RemoveShot("wide", shot)
        action.execute(scene)
        assertNull(scene.shots["wide"])
        action.undo(scene)
        assertEquals(shot, scene.shots["wide"])
    }


    @Test
    fun `AddBeat adds and undo removes`() {
        val beat = EditorBeat(id = "at_podium")
        AddBeat(beat).execute(scene)
        assertEquals(beat, scene.beats["at_podium"])
        AddBeat(beat).undo(scene)
        assertNull(scene.beats["at_podium"])
    }

    @Test
    fun `RemoveBeat removes and undo re-adds`() {
        val beat = EditorBeat(id = "extra")
        scene.beats["extra"] = beat
        val action = RemoveBeat("extra", beat)
        action.execute(scene)
        assertNull(scene.beats["extra"])
        action.undo(scene)
        assertEquals(beat, scene.beats["extra"])
    }

    @Test
    fun `ChangeBeatProperty edits speed and undo reverts`() {
        val beat = EditorBeat(id = "main", speed = 1.0f)
        scene.beats["main"] = beat
        val action = ChangeBeatProperty<Float>(
            beatId = "main",
            setter = { b, v -> b.speed = v },
            oldValue = 1.0f,
            newValue = 0.5f
        )
        action.execute(scene)
        assertEquals(0.5f, scene.beats["main"]?.speed)
        action.undo(scene)
        assertEquals(1.0f, scene.beats["main"]?.speed)
    }


    @Test
    fun `AddTransition appends and undo removes`() {
        val tr = EditorBeatTransition(sourceBeatId = "a", targetBeatId = "b")
        AddTransition(tr).execute(scene)
        assertEquals(1, scene.transitions.size)
        AddTransition(tr).undo(scene)
        assertEquals(0, scene.transitions.size)
    }

    @Test
    fun `RemoveTransition preserves index on undo`() {
        val t0 = EditorBeatTransition(sourceBeatId = "a", targetBeatId = "b")
        val t1 = EditorBeatTransition(sourceBeatId = "b", targetBeatId = "c")
        scene.transitions.addAll(listOf(t0, t1))
        val action = RemoveTransition(index = 0, transition = t0)
        action.execute(scene)
        assertEquals(listOf(t1), scene.transitions.toList())
        action.undo(scene)
        assertEquals(listOf(t0, t1), scene.transitions.toList())
    }

    @Test
    fun `EditTransitionCondition changes condition and undo reverts`() {
        scene.transitions.add(EditorBeatTransition(sourceBeatId = "a", targetBeatId = "b", condition = "1.0"))
        val action = EditTransitionCondition(index = 0, oldCondition = "1.0", newCondition = "t > 3")
        action.execute(scene)
        assertEquals("t > 3", scene.transitions[0].condition)
        action.undo(scene)
        assertEquals("1.0", scene.transitions[0].condition)
    }


    @Test
    fun `RenameScene changes id and undo reverts`() {
        RenameScene(oldId = scene.id, newId = "gym_intro").execute(scene)
        assertEquals("gym_intro", scene.id)
        RenameScene(oldId = "new_scene", newId = "gym_intro").undo(scene)
        assertEquals("new_scene", scene.id)
    }

    @Test
    fun `ChangeStartBeat swaps start beat id and undo reverts`() {
        val action = ChangeStartBeat(oldStartBeatId = "main", newStartBeatId = "intro")
        action.execute(scene)
        assertEquals("intro", scene.startBeatId)
        action.undo(scene)
        assertEquals("main", scene.startBeatId)
    }


    @Test
    fun `CompoundSceneAction runs actions in order and undo in reverse`() {
        val log = mutableListOf<String>()
        val noteA = object : SceneEditorAction {
            override fun execute(scene: EditorScene) { log.add("A-do") }
            override fun undo(scene: EditorScene) { log.add("A-undo") }
        }
        val noteB = object : SceneEditorAction {
            override fun execute(scene: EditorScene) { log.add("B-do") }
            override fun undo(scene: EditorScene) { log.add("B-undo") }
        }
        val compound = CompoundSceneAction(listOf(noteA, noteB))
        compound.execute(scene)
        compound.undo(scene)
        assertEquals(listOf("A-do", "B-do", "B-undo", "A-undo"), log)
    }


    @Test
    fun `EditorScene execute pushes to undo stack and marks dirty`() {
        assertFalse(scene.isDirty)
        assertFalse(scene.canUndo())
        scene.execute(AddActor(EditorActor(id = "hero", kind = ActorKind.PlayerTakeover)))
        assertTrue(scene.isDirty)
        assertTrue(scene.canUndo())
        assertFalse(scene.canRedo())
    }

    @Test
    fun `EditorScene undo applies reverse and enables redo`() {
        scene.execute(AddActor(EditorActor(id = "hero", kind = ActorKind.PlayerTakeover)))
        assertTrue(scene.undo())
        assertNull(scene.actors["hero"])
        assertTrue(scene.canRedo())
        assertFalse(scene.canUndo())
    }

    @Test
    fun `EditorScene redo re-applies action`() {
        scene.execute(AddActor(EditorActor(id = "hero", kind = ActorKind.PlayerTakeover)))
        scene.undo()
        assertTrue(scene.redo())
        assertNotNull(scene.actors["hero"])
    }

    @Test
    fun `EditorScene execute clears redo stack`() {
        scene.execute(AddActor(EditorActor(id = "a", kind = ActorKind.RealEntity)))
        scene.undo()
        assertTrue(scene.canRedo())
        scene.execute(AddActor(EditorActor(id = "b", kind = ActorKind.RealEntity)))
        assertFalse(scene.canRedo())
    }

    @Test
    fun `ChangeSceneProperty generic edits target and reverts`() {
        val beat = EditorBeat(id = "main", speed = 1.0f)
        scene.beats["main"] = beat
        val action = ChangeSceneProperty<Float, EditorBeat>(
            target = beat,
            setter = { b, v -> b.speed = v },
            oldValue = 1.0f,
            newValue = 2.0f
        )
        action.execute(scene)
        assertEquals(2.0f, beat.speed)
        action.undo(scene)
        assertEquals(1.0f, beat.speed)
    }
}
