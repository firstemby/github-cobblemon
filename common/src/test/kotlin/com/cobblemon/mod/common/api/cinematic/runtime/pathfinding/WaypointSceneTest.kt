/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.LookAtTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.PoseLockTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.api.cinematic.registry.SceneBuiltinSources
import com.cobblemon.mod.common.api.cinematic.registry.SceneSourceRegistry
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorTrackTypes
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActorDirection
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.entity.PoseType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WaypointSceneTest {
    private val path = WaypointPathfinding(listOf(Vec3.ZERO, Vec3(3.0, 0.0, 0.0)))
    private fun scene(vararg tracks: ActorTrack) = Scene("test", startBeatId = "main",
        actors = mapOf("hero" to ActorDeclaration("hero", ActorKind.SynthesizedPokemon)),
        beats = mapOf("main" to Beat("main", duration = 30f,
            actorDirections = mapOf("hero" to ActorDirection(tracks = tracks.toList())))))

    @Test fun `pathfinding permits a separate look at track`() {
        assertTrue(scene(path, LookAtTrack(Vec3.ZERO)).validate().isEmpty())
    }

    @Test fun `rejects competing pathfinding and movement tracks`() {
        assertTrue(scene(path, path).validate().any { it.message == Component.translatable("cobblemon.cinematic_editor.validation.movement_conflict") })
        assertTrue(scene(path, KeyframeMovement(listOf(KeyframeMovement.Kf(0f, Vec3.ZERO))))
            .validate().any { it.message == Component.translatable("cobblemon.cinematic_editor.validation.movement_conflict") })
        assertTrue(scene(path, PoseLockTrack(PoseType.WALK)).validate().any { it.message == Component.translatable("cobblemon.cinematic_editor.validation.movement_conflict") })
    }

    @Test fun `registry can deserialize waypoint track`() {
        SceneBuiltinSources.register()
        val parsed = SceneSourceRegistry.deserializeActorTrack("pathfinding", ActorTrackTypes.entryFor("pathfinding")!!.defaultBody())
        assertInstanceOf(WaypointPathfinding::class.java, parsed)
    }

    @Test fun `waypoint data round trips through editor serialization`() {
        SceneBuiltinSources.register()
        val editor = EditorScene.createDefault()
        editor.actors["hero"] = EditorActor("hero", ActorKind.SynthesizedPokemon)
        val body = ActorTrackTypes.entryFor("pathfinding")!!.defaultBody()
        editor.beats.getValue("main").actorDirections["hero"] =
            EditorActorDirection(tracks = mutableListOf(EditorTrack("pathfinding", body)))
        val json = SceneSerializer.toJson(editor)
        val restored = SceneSerializer.fromScene(SceneSerializer.toScene(editor), sourceJson = json)
        val result = restored.beats.getValue("main").actorDirections.getValue("hero").tracks.single()
        assertEquals("pathfinding", result.trackType)
        assertEquals(WaypointPathfinding.fromJson(body), WaypointPathfinding.fromJson(result.data))
    }
}
