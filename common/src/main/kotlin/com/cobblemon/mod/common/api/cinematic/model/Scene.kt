/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.runtime.ActorFraming
import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.runtime.ActorDependencyOrder
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget

data class Scene(
    val id: String,
    val parameters: Map<String, ParameterDefinition> = emptyMap(),
    val actors: Map<String, ActorDeclaration> = emptyMap(),
    val shots: Map<String, Shot> = emptyMap(),
    val feeds: List<FeedDeclaration> = emptyList(),
    val startBeatId: String,
    val beats: Map<String, Beat>,
    val transitions: List<BeatTransition> = emptyList(),
    val version: Int = 2,
    val damageImmunityDuringTakeover: Boolean = false,
    val disconnectPolicy: DisconnectPolicy = DisconnectPolicy.END_SCENE
) {
    init {
        require(id.isNotBlank()) { "Scene id must be non-blank" }
        require(startBeatId.isNotBlank()) { "Scene startBeatId must be non-blank" }
        require(version == 2) { "Scene.version must be 2, got $version" }
    }

    fun getBeat(beatId: String): Beat? = beats[beatId]

    fun getShot(shotId: String): Shot? = shots[shotId]

    fun getActor(actorId: String): ActorDeclaration? = actors[actorId]

    fun getFeed(feedId: String): FeedDeclaration? = feeds.firstOrNull { it.id == feedId }

    fun getTransitionsFrom(beatId: String): List<BeatTransition> =
        transitions.filter { it.sourceBeatId == beatId }

    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (startBeatId !in beats) {
            errors.add(ValidationError(
                path = "startBeatId",
                message = Component.translatable("cobblemon.cinematic_editor.validation.start_missing", startBeatId)
            ))
        }

        actors.forEach { (key, actor) ->
            if (actor.id != key) {
                errors.add(ValidationError(
                    path = "actors[$key].id",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.actor_id", key, actor.id)
                ))
            }
        }

        shots.forEach { (key, shot) ->
            val framingActors = (shot.position as? OrbitPosition)?.actorIds.orEmpty() +
                listOfNotNull((shot.position as? OrbitPosition)?.bearingActorId) +
                ((shot.rotation as? LookAt)?.target as? LookAtTarget.Actors)?.actorIds.orEmpty()
            framingActors.distinct().filter { it !in actors && it !in ActorFraming.groups }.forEach { actorId ->
                errors.add(ValidationError(path = "shots[$key]", message = Component.translatable("cobblemon.cinematic_editor.validation.framing_actor", actorId)))
            }
            if (shot.id != key) {
                errors.add(ValidationError(
                    path = "shots[$key].id",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.shot_id", key, shot.id)
                ))
            }
        }

        val feedIds = feeds.map { it.id }
        val duplicateFeedIds = feedIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        duplicateFeedIds.forEach { duplicateId ->
            errors.add(ValidationError(
                path = "feeds",
                message = Component.translatable("cobblemon.cinematic_editor.validation.duplicate_feed", duplicateId)
            ))
        }

        val cueIds = mutableSetOf<String>()
        beats.forEach { (beatKey, beat) ->
            beat.actorDirections.forEach { (actorId, direction) ->
                if (direction.tracks.any { it.trackType == "pathfinding" } &&
                    direction.tracks.count { it.trackType in setOf("pathfinding", "spline_movement", "keyframe_movement", "follow_actor", "attach", "pose_lock") } > 1) {
                    errors.add(ValidationError(
                        path = "beats[$beatKey].actorDirections[$actorId].tracks",
                        message = Component.translatable("cobblemon.cinematic_editor.validation.movement_conflict")
                    ))
                }
            }
            beat.cues.forEachIndexed { index, cue ->
                if (!cueIds.add(cue.id)) errors.add(ValidationError(
                    path = "beats[$beatKey].cues[$index].id", message = Component.translatable("cobblemon.cinematic_editor.validation.duplicate_cue", cue.id)))
                if (beat.duration != null && cue.time > beat.duration) errors.add(ValidationError(
                    path = "beats[$beatKey].cues[$index].time", message = Component.translatable("cobblemon.cinematic_editor.validation.late_cue", cue.id)))
            }
            try {
                ActorDependencyOrder.resolve(actors.keys, beat)
            } catch (error: CinematicValidationException) {
                errors.add(ValidationError(path = "beats[$beatKey].actorDirections", message = error.reason))
            }
            if (beat.id != beatKey) {
                errors.add(ValidationError(
                    path = "beats[$beatKey].id",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.beat_id", beatKey, beat.id)
                ))
            }
            beat.actorDirections.keys.forEach { actorRef ->
                if (actorRef !in actors) {
                    errors.add(ValidationError(
                        path = "beats[$beatKey].actorDirections[$actorRef]",
                        message = Component.translatable("cobblemon.cinematic_editor.validation.actor_missing", actorRef)
                    ))
                }
            }
            beat.cuts.forEach { (feedRef, cutTrack) ->
                if (feedRef !in feedIds) {
                    errors.add(ValidationError(
                        path = "beats[$beatKey].cuts[$feedRef]",
                        message = Component.translatable("cobblemon.cinematic_editor.validation.feed_missing", feedRef)
                    ))
                }
                cutTrack.entries.forEach { entry ->
                    if (entry.shotId !in shots) {
                        errors.add(ValidationError(
                            path = "beats[$beatKey].cuts[$feedRef]",
                            message = Component.translatable("cobblemon.cinematic_editor.validation.shot_missing", entry.shotId, entry.time)
                        ))
                    }
                }
            }
        }

        transitions.forEachIndexed { index, transition ->
            if (transition.sourceBeatId !in beats) {
                errors.add(ValidationError(
                    path = "transitions[$index].sourceBeatId",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.source_missing", transition.sourceBeatId)
                ))
            }
            if (!transition.endsScene && transition.targetBeatId !in beats) {
                errors.add(ValidationError(
                    path = "transitions[$index].targetBeatId",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.target_missing", transition.targetBeatId)
                ))
            }
        }

        val graph = transitions.filter { !it.endsScene && it.sourceBeatId in beats && it.targetBeatId in beats }
            .groupBy({ it.sourceBeatId }, { it.targetBeatId })
        val reachable = findReachableBeats(graph)
        beats.keys.forEach { beatId ->
            if (beatId !in reachable) {
                errors.add(ValidationError(
                    path = "beats[$beatId]",
                    message = Component.translatable("cobblemon.cinematic_editor.validation.unreachable", startBeatId),
                    severity = ValidationError.Severity.WARNING
                ))
            }
        }

        val cycle = findCycle(graph)
        if (cycle != null) {
            errors.add(ValidationError(
                path = "transitions",
                message = Component.translatable("cobblemon.cinematic_editor.validation.cycle", cycle.joinToString(" -> ")),
                severity = ValidationError.Severity.WARNING
            ))
        }

        return errors
    }

    private fun findReachableBeats(graph: Map<String, List<String>>): Set<String> {
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        if (startBeatId in beats) {
            queue.add(startBeatId)
            reachable.add(startBeatId)
        }
        while (queue.isNotEmpty()) {
            for (target in graph[queue.removeFirst()].orEmpty()) {
                if (reachable.add(target)) queue.addLast(target)
            }
        }
        return reachable
    }

    private fun findCycle(graph: Map<String, List<String>>): List<String>? {
        val visited = mutableSetOf<String>()
        val active = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        val stack = ArrayDeque<Iterator<String>>()
        fun enter(id: String) {
            visited.add(id)
            active[id] = path.size
            path.add(id)
            stack.addLast(graph[id].orEmpty().iterator())
        }
        for (id in beats.keys) {
            if (id in visited) continue
            enter(id)
            while (stack.isNotEmpty()) {
                val remaining = stack.last()
                if (remaining.hasNext()) {
                    val target = remaining.next()
                    val cycleStart = active[target]
                    if (cycleStart != null) return path.subList(cycleStart, path.size) + target
                    if (target !in visited) enter(target)
                } else {
                    stack.removeLast()
                    active.remove(path.removeAt(path.lastIndex))
                }
            }
        }
        return null
    }
}
