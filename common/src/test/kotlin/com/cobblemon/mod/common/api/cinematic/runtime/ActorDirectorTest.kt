/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActorDirectorTest {

    private fun scene(actors: Map<String, ActorDeclaration>, beats: Map<String, Beat>): Scene = Scene(
        id = "t",
        startBeatId = beats.keys.first(),
        actors = actors,
        beats = beats
    )

    @Test
    fun `visibility carries over when direction is null`() {
        val actors = mapOf("hero" to ActorDeclaration("hero", ActorKind.RealEntity))
        val beat1 = Beat(id = "b1", actorDirections = mapOf("hero" to ActorDirection(visible = true)))
        val beat2 = Beat(id = "b2")
        val director = ActorDirector(scene(actors, mapOf("b1" to beat1, "b2" to beat2)), emptyMap())

        director.onBeatEnter(beat1)
        assertTrue(director.isVisible("hero"))
        director.onBeatEnter(beat2)
        assertTrue(director.isVisible("hero"), "Visibility should carry over from previous beat")
    }

    @Test
    fun `visibility flips when direction sets visible false`() {
        val actors = mapOf("hero" to ActorDeclaration("hero", ActorKind.RealEntity))
        val beat1 = Beat(id = "b1", actorDirections = mapOf("hero" to ActorDirection(visible = true)))
        val beat2 = Beat(id = "b2", actorDirections = mapOf("hero" to ActorDirection(visible = false)))
        val director = ActorDirector(scene(actors, mapOf("b1" to beat1, "b2" to beat2)), emptyMap())

        director.onBeatEnter(beat1)
        assertTrue(director.isVisible("hero"))
        director.onBeatEnter(beat2)
        assertFalse(director.isVisible("hero"))
    }

    @Test
    fun `KeyframeMovement interpolates linearly between points`() {
        val track = KeyframeMovement(listOf(
            KeyframeMovement.Kf(0f, Vec3(0.0, 0.0, 0.0)),
            KeyframeMovement.Kf(1f, Vec3(10.0, 0.0, 0.0))
        ))
        assertEquals(0.0, track.evaluate(0f).x, 0.01)
        assertEquals(5.0, track.evaluate(0.5f).x, 0.01)
        assertEquals(10.0, track.evaluate(1f).x, 0.01)
        assertEquals(10.0, track.evaluate(2f).x, 0.01)
    }

    @Test
    fun `isPlayerTakeoverActive returns false without a Player binding`() {
        val actors = mapOf("hero" to ActorDeclaration("hero", ActorKind.PlayerTakeover))
        val beat = Beat(id = "main", actorDirections = mapOf("hero" to ActorDirection(visible = true)))
        val director = ActorDirector(scene(actors, mapOf("main" to beat)), emptyMap())
        director.onBeatEnter(beat)
        assertFalse(director.isPlayerTakeoverActive("hero"), "Without a Player binding, takeover is not active")
    }
}
