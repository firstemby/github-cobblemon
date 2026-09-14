/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeBeatProperty
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.registry.SceneSourceRegistry
import com.google.gson.JsonParser
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SceneSerializerVersionDispatchTest {

    @Test
    fun `cues survive edits undo redo and file round trip`(@TempDir tmp: Path) {
        val scene = EditorScene.createDefault()
        val beat = scene.beats.getValue(scene.startBeatId)
        val cues = listOf(SceneCue("handoff", 4f), SceneCue("reveal", 1f))
        scene.execute(ChangeBeatProperty(beat.id, { b, value -> b.cues = value }, beat.cues, cues))
        assertEquals(cues, beat.cues)
        scene.undo()
        assertTrue(beat.cues.isEmpty())
        scene.redo()
        val path = tmp.resolve("cues.json")
        SceneSerializer.saveToFile(scene, path)
        val reopened = SceneSerializer.loadFromFile(path)!!
        assertEquals(cues.sortedBy { it.time }, reopened.beats.getValue(beat.id).cues)
        assertEquals(cues.sortedBy { it.time }, SceneSerializer.toScene(reopened).beats.getValue(beat.id).cues)
    }

    @Test
    fun `preview bindings survive save and reopen`(@TempDir tmp: Path) {
        val scene = EditorScene.createDefault()
        scene.actors["hero"] = EditorActor("hero", ActorKind.SynthesizedPokemon,
            previewSpecies = "pikachu", previewAspects = mutableSetOf("shiny"), previewScale = 1.5f)
        val path = tmp.resolve("preview.json")
        SceneSerializer.saveToFile(scene, path)
        val actor = SceneSerializer.loadFromFile(path)!!.actors.getValue("hero")
        assertEquals("pikachu", actor.previewSpecies)
        assertEquals(setOf("shiny"), actor.previewAspects)
        assertEquals(1.5f, actor.previewScale)
    }

    @Test
    fun `saving and reopening preserves shots and unedited GUI tracks`(@TempDir tmp: Path) {
        val registry = SceneSourceRegistry
        registry.registerPositionSource("fixed", FixedPosition::fromJson)
        registry.registerRotationSource("fixed", FixedRotation::fromJson)
        val original = JsonParser.parseString("""
            {"version":2,"sceneId":"round_trip","startBeatId":"main",
             "shots":{"wide":{"id":"wide","position":{"type":"fixed","position":{"x":1,"y":64,"z":2}},
               "rotation":{"type":"fixed","yaw":10,"pitch":5}}},
             "beats":{"main":{"id":"main","guiTrack":{"actions":[{"time":1,"action":"CLOSE","guiId":"test"}]},
               "actorDirections":{"hero":{"startPose":{"position":{"x":1,"y":64,"z":2},
                 "rotation":{"yaw":20,"pitch":0},"blendDurationSeconds":2}}}}}}
        """).asJsonObject
        val path = tmp.resolve("cinematic.json")
        path.writeText(original.toString())
        val loaded = SceneSerializer.loadFromFile(path)!!
        assertEquals(setOf("wide"), loaded.shots.keys)
        loaded.markDirty()
        SceneSerializer.saveToFile(loaded, path)
        assertFalse(loaded.isDirty)
        assertEquals(path, loaded.sourcePath)
        val reloaded = SceneSerializer.loadFromFile(path)!!
        val json = SceneSerializer.toJson(reloaded)
        assertEquals(original.get("shots"), json.get("shots"))
        assertEquals(original.getAsJsonObject("beats").getAsJsonObject("main").get("guiTrack"),
            json.getAsJsonObject("beats").getAsJsonObject("main").get("guiTrack"))
        assertEquals(2f, reloaded.beats.getValue("main").actorDirections.getValue("hero").startPose!!.blendDurationSeconds)
    }

    @Test
    fun `loadFromFile returns null for v1 document`(@TempDir tmp: Path) {
        val v1 = tmp.resolve("v1.json")
        v1.writeText(
            """
            {
              "version": 1,
              "timelineId": "legacy",
              "startStateId": "default",
              "states": { "default": { "stateId": "default", "tracks": [] } }
            }
            """.trimIndent()
        )
        assertNull(SceneSerializer.loadFromFile(v1))
    }

    @Test
    fun `loadFromFile returns null for versionless (implicitly v1) document`(@TempDir tmp: Path) {
        val legacy = tmp.resolve("legacy.json")
        legacy.writeText(
            """
            {
              "timelineId": "old",
              "startStateId": "default",
              "states": { "default": { "stateId": "default", "tracks": [] } }
            }
            """.trimIndent()
        )
        assertNull(SceneSerializer.loadFromFile(legacy))
    }

    @Test
    fun `loadFromFile parses v2 document into EditorScene`(@TempDir tmp: Path) {
        val v2 = tmp.resolve("v2.json")
        v2.writeText(
            """
            {
              "version": 2,
              "sceneId": "test_scene",
              "startBeatId": "main",
              "beats": {
                "main": { "id": "main" }
              }
            }
            """.trimIndent()
        )
        val editor = SceneSerializer.loadFromFile(v2)
        assertNotNull(editor)
        assertEquals("test_scene", editor!!.id)
        assertEquals("main", editor.startBeatId)
        assertEquals(1, editor.beats.size)
    }

    @Test
    fun `saveToFile then loadFromFile round-trips scene id`(@TempDir tmp: Path) {
        val path = tmp.resolve("round_trip.json")
        val editor = EditorScene(id = "round_tripped", startBeatId = "main").also {
            it.beats["main"] = EditorBeat(id = "main")
        }
        SceneSerializer.saveToFile(editor, path)
        val loaded = SceneSerializer.loadFromFile(path)
        assertNotNull(loaded)
        assertEquals("round_tripped", loaded!!.id)
        assertEquals("main", loaded.startBeatId)
        assertEquals(2, loaded.version)
    }
}
