/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AttachTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.FollowActor
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActorDependencyOrderTest {
    @Test
    fun `dependencies precede followers regardless of declaration order`() {
        val beat = Beat("main", actorDirections = mapOf(
            "child" to ActorDirection(tracks = listOf(AttachTrack("follower"))),
            "follower" to ActorDirection(tracks = listOf(FollowActor("leader", Vec3.ZERO, 1f)))
        ))
        assertEquals(listOf("leader", "follower", "child"),
            ActorDependencyOrder.resolve(linkedSetOf("child", "follower", "leader"), beat))
    }

    @Test
    fun `cycles and dangling dependencies explain the offending beat`() {
        val cycle = Beat("intro", actorDirections = mapOf(
            "a" to ActorDirection(tracks = listOf(AttachTrack("b"))),
            "b" to ActorDirection(tracks = listOf(AttachTrack("a")))
        ))
        val error = assertThrows(CinematicValidationException::class.java) {
            ActorDependencyOrder.resolve(setOf("a", "b"), cycle)
        }
        assertEquals(Component.translatable("cobblemon.cinematic_editor.validation.actor_dependency_cycle", "intro", "a -> b -> a"), error.reason)
        val missing = assertThrows(CinematicValidationException::class.java) {
            ActorDependencyOrder.resolve(setOf("a"), cycle)
        }
        assertEquals(Component.translatable("cobblemon.cinematic_editor.validation.actor_dependency_missing", "a", "b", "intro"), missing.reason)
    }

    @Test fun `long dependency chains validate without recursive stack growth`() {
        val ids = (0 until 10_000).map { "actor_$it" }
        val beat = Beat("long", actorDirections = ids.dropLast(1).mapIndexed { index, id ->
            id to ActorDirection(tracks = listOf(AttachTrack(ids[index + 1])))
        }.toMap())
        assertEquals(ids.reversed(), ActorDependencyOrder.resolve(ids.toSet(), beat))
    }
}
