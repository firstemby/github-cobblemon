/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.google.gson.JsonArray
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.registry.SceneBuiltinSources
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SceneSerializerActorTrackRoundTripTest {
    @Test
    fun `actor track json is read back into the editor model`() {
        val editor = EditorScene(id = "s", startBeatId = "main")
        SceneBuiltinSources.register()
        editor.actors["hero"] = EditorActor("hero", ActorKind.RealEntity)
        editor.beats["main"] = EditorBeat(id = "main").also { b ->
            b.actorDirections["hero"] = EditorActorDirection().also { d ->
                val data = JsonObject().also { obj ->
                    val arr = JsonArray()
                    val kf = JsonObject(); kf.addProperty("time", 1.5f)
                    kf.add("position", JsonObject().apply { addProperty("x", 1); addProperty("y", 64); addProperty("z", 2) })
                    arr.add(kf); obj.add("keyframes", arr)
                }
                d.tracks.add(EditorTrack("keyframe_movement", data))
            }
        }
        val json = SceneSerializer.toJson(editor)

        val target = SceneSerializer.fromScene(SceneSerializer.toScene(editor), sourceJson = json)

        val tracks = target.beats["main"]!!.actorDirections["hero"]!!.tracks
        assertEquals(1, tracks.size)
        assertEquals("keyframe_movement", tracks[0].trackType)
        assertEquals(1.5f, tracks[0].data.getAsJsonArray("keyframes")[0].asJsonObject.get("time").asFloat, 1e-3f)
    }
}
