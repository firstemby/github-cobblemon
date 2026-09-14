/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AttachTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.FollowActor
import net.minecraft.network.chat.Component

object ActorDependencyOrder {
    fun resolve(actorIds: Set<String>, beat: Beat): List<String> {
        val dependencies = actorIds.associateWith { id ->
            beat.actorDirections[id]?.tracks.orEmpty().mapNotNull { track ->
                val target = when (track) {
                    is FollowActor -> track.targetActorId
                    is AttachTrack -> track.parentActorId
                    else -> null
                }
                if (target != null) {
                    if (target !in actorIds) throw CinematicValidationException(Component.translatable(
                        "cobblemon.cinematic_editor.validation.actor_dependency_missing", id, target, beat.id))
                }
                target
            }
        }
        val ordered = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val active = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        val stack = ArrayDeque<Iterator<String>>()
        fun enter(id: String) {
            active[id] = path.size
            path.add(id)
            stack.addLast(dependencies.getValue(id).iterator())
        }
        for (id in actorIds) {
            if (id in visited) continue
            enter(id)
            while (stack.isNotEmpty()) {
                val remaining = stack.last()
                if (remaining.hasNext()) {
                    val target = remaining.next()
                    val cycleStart = active[target]
                    if (cycleStart != null) throw CinematicValidationException(Component.translatable(
                        "cobblemon.cinematic_editor.validation.actor_dependency_cycle", beat.id,
                        (path.subList(cycleStart, path.size) + target).joinToString(" -> ")))
                    if (target !in visited) enter(target)
                } else {
                    stack.removeLast()
                    val completed = path.removeAt(path.lastIndex)
                    active.remove(completed)
                    visited.add(completed)
                    ordered.add(completed)
                }
            }
        }
        return ordered
    }
}
