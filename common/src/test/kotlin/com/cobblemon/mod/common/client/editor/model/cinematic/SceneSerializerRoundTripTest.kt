/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneBuiltinSources
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneSerializerRoundTripTest {

    @Test
    fun `editing imported raw channels and tracks cannot change source JSON or another editor`() {
        val source = richSource()
        val original = source.deepCopy()
        val scene = parse(source)
        val first = SceneSerializer.fromScene(scene, sourceJson = source)
        val second = SceneSerializer.fromScene(scene, sourceJson = source)
        val secondBefore = SceneSerializer.toJson(second)
        val shot = first.shots.getValue("trainer")
        shot.positionData.getAsJsonObject("position").addProperty("x", 999)
        shot.rotationData.addProperty("yaw", 90)
        shot.fovData!!.addProperty("value", 100)
        shot.rollData!!.addProperty("value", 12)
        shot.modifiers[0].getAsJsonObject("position").addProperty("y", 8)
        val beat = first.beats.getValue("entrance")
        beat.actorDirections.getValue("hero").tracks[0].data.getAsJsonArray("cues")[0].asJsonObject.addProperty("name", "changed")
        beat.guiTrackData!!.getAsJsonArray("actions")[0].asJsonObject.addProperty("guiId", "changed")
        assertEquals(original, source)
        assertEquals(secondBefore, SceneSerializer.toJson(second))
        assertNotEquals(secondBefore, SceneSerializer.toJson(first))
    }

    @Test
    fun `import and export retain shot channels modifiers actor tracks and GUI actions`() {
        val source = richSource()
        val editor = SceneSerializer.fromScene(parse(source), sourceJson = source)
        val exported = SceneSerializer.toJson(editor)
        assertEquals(source.get("shots"), exported.get("shots"))
        val sourceBeat = source.getAsJsonObject("beats").getAsJsonObject("entrance")
        val exportedBeat = exported.getAsJsonObject("beats").getAsJsonObject("entrance")
        assertEquals(sourceBeat.get("actorDirections"), exportedBeat.get("actorDirections"))
        assertEquals(sourceBeat.get("guiTrack"), exportedBeat.get("guiTrack"))
        val restored = SceneSerializer.toScene(editor)
        assertEquals(1, restored.shots.size)
        assertEquals(1, restored.beats.getValue("entrance").actorDirections.getValue("hero").tracks.size)
        assertEquals(1, restored.beats.getValue("entrance").guiTrack!!.actions.size)
    }

    private fun richSource(): JsonObject = JsonParser.parseString("""
        {"version":2,"sceneId":"gym_intro","startBeatId":"entrance",
         "actors":{"hero":{"id":"hero","kind":"player_takeover"},
                   "starter":{"id":"starter","kind":"synthesized_pokemon"}},
         "feeds":[{"id":"primary","kind":{"type":"fullscreen"}},
                  {"id":"pip","kind":{"type":"pip","corner":"top_right","size":0.3}}],
         "shots":{"trainer":{"id":"trainer",
             "position":{"type":"fixed","position":{"x":1,"y":64,"z":2}},
             "rotation":{"type":"fixed","yaw":10,"pitch":5},
             "fov":{"type":"fixed","value":70},"roll":{"type":"fixed","value":1},
             "lens":{"aspectCrop":2.0,"letterbox":0.125},
             "modifiers":[{"type":"offset","position":{"x":0,"y":1,"z":0}}]}},
         "beats":{"entrance":{"id":"entrance","duration":5,
             "cuts":{"primary":{"entries":[{"time":0,"shotId":"trainer","transition":{"type":"instant"}}]}},
             "actorDirections":{"hero":{"tracks":[{"trackType":"animation","cues":[{"time":1,"name":"wave"}]}]}},
             "guiTrack":{"actions":[{"time":1,"action":"CLOSE","guiId":"test"}]}},
             "at_podium":{"id":"at_podium","speed":0.8,"isLooping":true}},
         "transitions":[{"sourceBeatId":"entrance","targetBeatId":"at_podium","condition":"1.0",
                         "transition":{"type":"blend","duration":0.8}}]}
    """).asJsonObject

    private fun parse(source: JsonObject): Scene {
        SceneBuiltinSources.register()
        return GsonBuilder().registerTypeAdapter(Scene::class.java, SceneDeserializer).create().fromJson(source, Scene::class.java)
    }

    private fun richEditor(): EditorScene {
        val source = richSource()
        return SceneSerializer.fromScene(parse(source), sourceJson = source)
    }

    @Test
    fun `Scene to EditorScene preserves structural fields`() {
        val editor = richEditor()
        assertEquals("gym_intro", editor.id)
        assertEquals("entrance", editor.startBeatId)
        assertEquals(2, editor.actors.size)
        assertEquals(ActorKind.PlayerTakeover, editor.actors["hero"]?.kind)
        assertEquals(2, editor.feeds.size)
        assertEquals(2, editor.beats.size)
        assertEquals(5f, editor.beats["entrance"]?.duration)
        assertEquals(0.8f, editor.beats["at_podium"]?.speed)
        assertTrue(editor.beats["at_podium"]?.isLooping == true)
        assertEquals(1, editor.transitions.size)
        assertEquals("1.0", editor.transitions[0].condition)
    }

    @Test
    fun `EditorScene to JSON to Scene round-trips structural fields`() {
        val editor = richEditor()
        val scene = SceneSerializer.toScene(editor)
        assertEquals("gym_intro", scene.id)
        assertEquals("entrance", scene.startBeatId)
        assertEquals(2, scene.actors.size)
        assertEquals(2, scene.feeds.size)
        assertEquals(2, scene.beats.size)
        assertEquals(1, scene.transitions.size)
        assertEquals(2, scene.version)
    }

    @Test
    fun `empty feed tracks are omitted while populated tracks round-trip`() {
        val editor = richEditor()
        val beat = editor.beats.getValue("entrance")
        beat.cuts["primary"] = EditorCutTrack()
        beat.cuts["pip"] = EditorCutTrack().apply {
            entries.add(EditorCutEntry(time = 0f, shotId = "trainer"))
        }
        editor.beats.getValue("at_podium").cuts["primary"] = EditorCutTrack()

        val json = SceneSerializer.toJson(editor).getAsJsonObject("beats")
        assertEquals(setOf("pip"), json.getAsJsonObject("entrance").getAsJsonObject("cuts").keySet())
        assertFalse(json.getAsJsonObject("at_podium").has("cuts"))

        val scene = SceneSerializer.toScene(editor)
        assertEquals(setOf("pip"), scene.beats.getValue("entrance").cuts.keys)
        assertEquals("trainer", scene.beats.getValue("entrance").cuts.getValue("pip").entries.single().shotId)
        assertTrue(scene.beats.getValue("at_podium").cuts.isEmpty())
        assertTrue(beat.cuts.containsKey("primary"))
    }

    @Test
    fun `toJson writes version 2`() {
        val editor = richEditor()
        val json = SceneSerializer.toJson(editor)
        assertEquals(2, json.get("version").asInt)
    }

    @Test
    fun `default EditorScene round-trips to a valid Scene`() {
        val editor = EditorScene.createDefault()
        val scene = SceneSerializer.toScene(editor)
        assertEquals("new_scene", scene.id)
        assertEquals("main", scene.startBeatId)
        assertTrue(scene.validate().none { it.severity == ValidationError.Severity.ERROR })
    }
}
