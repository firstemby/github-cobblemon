/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.client.camera.tracks.CameraTrackDefinitionRegistry
import com.cobblemon.mod.common.client.camera.tracks.EventTrack
import com.cobblemon.mod.common.client.camera.tracks.FocusTrack
import com.cobblemon.mod.common.client.camera.tracks.PositionTrack
import com.cobblemon.mod.common.client.camera.tracks.TrackReferenceTrack
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.resolveBoolean

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID

class CameraTimelineRunner(
    val id: UUID = UUID.randomUUID(),
    initialContext: Map<String, MoValue> = emptyMap(),
    var origin: Vec3 = Vec3.ZERO,
    val runtime: MoLangRuntime = MoLangRuntime().setup()
) {
    var timeline: CameraTimeline? = null
        private set
    var currentState: CameraState? = null
        private set

    private var timeOnCurrentPath: Float = 0f
    private var totalTime: Float = 0f

    private var transitioningTo: CameraState? = null
    private var transitionStartTime: Float = 0f
    private var transitionDuration: Float = 0f
    private var transitionStartTransform: CameraTransform? = null

    private val customVariables = mutableMapOf<String, Double>()
    private val pathProgress = mutableMapOf<String, Float>()
    private val context = initialContext.toMutableMap()

    private val executedEvents = mutableSetOf<Pair<String, Float>>()
    private var lastEventCheckTime: Float = 0f

    var isActive = false
        private set

    fun startTimeline(timelineId: String): Boolean {
        timeline = CameraTimelineRegistry.getTimeline(timelineId)
        if (timeline == null) {
            Cobblemon.LOGGER.error("[CameraRunner] Failed to load timeline '$timelineId'")
            return false
        }

        val startState = timeline!!.getState(timeline!!.startStateId)
        if (startState == null) {
            Cobblemon.LOGGER.error("[CameraRunner] Timeline '$timelineId' start state '${timeline!!.startStateId}' not found")
            return false
        }

        pathProgress.clear()
        totalTime = 0f
        setState(startState)
        isActive = true
        return true
    }

    fun stop() {
        isActive = false
        pathProgress.clear()
        currentState = null
        timeline = null
    }

    private fun setState(state: CameraState) {
        storeCurrentProgress()
        currentState = state

        if (state.tracks.isEmpty()) {
            Cobblemon.LOGGER.error("[CameraRunner] State '${state.stateId}' has no tracks defined!")
        } else {
            Cobblemon.LOGGER.debug("[CameraRunner] State '${state.stateId}' using ${state.tracks.size} track(s)")
        }

        val startingProgress = if (state.rememberProgress) {
            val stateKey = state.stateId
            val normalizedProgress = pathProgress[stateKey] ?: 0f
            
            val duration = calculateStateDuration(state)
            normalizedProgress * duration
        } else 0f

        timeOnCurrentPath = startingProgress

        if (!state.rememberProgress) {
            executedEvents.clear()
            lastEventCheckTime = 0f
        }
    }

    fun tick(deltaSeconds: Float): CameraTransform? {
        if (!isActive || timeline == null || currentState == null) {
            return null
        }

        if (currentState!!.tracks.isEmpty()) {
            return null
        }

        totalTime += deltaSeconds
        timeOnCurrentPath += deltaSeconds * currentState!!.speed

        val duration = calculateStateDuration(currentState!!)

        if (!currentState!!.isLooping && duration > 0 && timeOnCurrentPath >= duration) {
            val hasTransitions = timeline?.getTransitionsFrom(currentState!!.stateId)?.isNotEmpty() ?: false
            if (!hasTransitions) {
                timeOnCurrentPath = duration
            } else {
                timeOnCurrentPath = duration
            }
        } else if (currentState!!.isLooping && duration > 0 && timeOnCurrentPath >= duration) {
            timeOnCurrentPath %= duration
        }

        bookmarkCurrentProgress()
        checkAndExecuteEvents()
        checkTransitions()
        return calculateCurrentTransform()
    }

    private fun calculateStateDuration(state: CameraState): Float {
        var maxTime = 0f

        for (track in state.tracks) {
            when (track) {
                is PositionTrack -> {
                    val trackMax = track.keyframes.maxOfOrNull { it.time } ?: 0f
                    if (trackMax > maxTime) maxTime = trackMax
                }
                is FocusTrack -> {
                    val trackMax = track.keyframes.maxOfOrNull { it.time } ?: 0f
                    if (trackMax > maxTime) maxTime = trackMax
                }
                is EventTrack -> {
                    val trackMax = track.events.maxOfOrNull { it.time } ?: 0f
                    if (trackMax > maxTime) maxTime = trackMax
                }
                is TrackReferenceTrack -> {
                    val referencedTrack = CameraTrackDefinitionRegistry.getTrack(track.trackId)
                    if (referencedTrack != null) {
                        val refDuration = calculateTrackDuration(referencedTrack)
                        if (refDuration > maxTime) maxTime = refDuration
                    }
                }
            }
        }

        val eventMax = state.events.maxOfOrNull { it.time } ?: 0f
        if (eventMax > maxTime) maxTime = eventMax

        return if (maxTime > 0f) maxTime else if (state.isLooping) 10f else 0f
    }

    private fun calculateTrackDuration(track: ICameraTrack): Float {
        return when (track) {
            is PositionTrack -> {
                track.keyframes.maxOfOrNull { it.time } ?: 0f
            }
            is FocusTrack -> {
                track.keyframes.maxOfOrNull { it.time } ?: 0f
            }
            is EventTrack -> {
                track.events.maxOfOrNull { it.time } ?: 0f
            }
            else -> 0f
        }
    }

    private fun checkAndExecuteEvents() {
        val state = currentState ?: return
        if (state.events.isEmpty()) return

        val previousTime = lastEventCheckTime
        val currentTime = timeOnCurrentPath
        lastEventCheckTime = currentTime

        for (event in state.events) {
            val eventKey = Pair(state.stateId, event.time)

            if (executedEvents.contains(eventKey) && currentTime >= event.time) {
                continue
            }

            val crossedEvent = if (previousTime <= currentTime) {
                event.time in previousTime..currentTime
            } else {
                event.time <= currentTime
            }

            if (crossedEvent) {
                executeEvent(event)
                executedEvents.add(eventKey)
            }
        }

        if (currentTime < previousTime && state.isLooping) {
            executedEvents.clear()
        }
    }

    private fun executeEvent(event: CameraEvent) {
        try {
            val evalContext = createMolangContext()
            val result = runtime.resolve(event.expression, evalContext)

            if (event.eventId != null) {
                Cobblemon.LOGGER.debug("[CameraRunner] Executed event '${event.eventId}' at time ${event.time}s, result: $result")
            }
        } catch (e: Exception) {
            val eventName = event.eventId ?: "unnamed"
            Cobblemon.LOGGER.error("[CameraRunner] Failed to execute event '$eventName': ${e.message}", e)
        }
    }

    private fun checkTransitions() {
        if (transitioningTo != null) return

        val state = currentState ?: return
        val transitions = timeline?.getTransitionsFrom(state.stateId) ?: return

        for (transition in transitions) {
            try {
                val evalContext = createMolangContext()
                val shouldTransition = runtime.resolveBoolean(transition.condition, evalContext)

                if (shouldTransition) {
                    val targetState = timeline?.getState(transition.targetStateId)
                    if (targetState == null) {
                        Cobblemon.LOGGER.error("[CameraRunner] Transition target state '${transition.targetStateId}' not found")
                        continue
                    }

                    if (transition.transitionType == TransitionType.INSTANT) {
                        setState(targetState)
                    } else {
                        transitioningTo = targetState
                        transitionStartTime = totalTime
                        transitionDuration = transition.blendDuration
                        transitionStartTransform = calculateCurrentTransform()
                    }
                    break
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CameraRunner] Error evaluating transition condition", e)
            }
        }
    }

    private fun calculateCurrentTransform(): CameraTransform? {
        val molangContext = createMolangContext()

        if (transitioningTo != null) {
            val transitionProgress = if (transitionDuration > 0)
                ((totalTime - transitionStartTime) / transitionDuration).coerceIn(0f, 1f)
            else 1f

            val easedProgress = CameraEasing.easeOutQuad(transitionProgress)

            if (transitionProgress >= 1f) {
                setState(transitioningTo!!)
                transitioningTo = null
                transitionStartTransform = null
            } else {
                val startTransform = transitionStartTransform ?: return null
                val targetTransform = if (transitioningTo!!.tracks.isNotEmpty()) {
                    evaluateTracksToTransform(transitioningTo!!.tracks, 0f, molangContext)
                } else null

                if (targetTransform != null) {
                    val blendedPosition = CameraInterpolationUtils.lerpPosition(
                        startTransform.position,
                        targetTransform.position,
                        easedProgress
                    )

                    val blendedRotation =
                        if (startTransform.manualRotation != null && targetTransform.manualRotation != null) {
                            CameraInterpolationUtils.slerpRotation(
                                startTransform.manualRotation,
                                targetTransform.manualRotation,
                                easedProgress
                            )
                        } else {
                            targetTransform.manualRotation
                        }

                    return CameraTransform(blendedPosition, targetTransform.focus, blendedRotation)
                }
            }
        }

        val state = currentState ?: return null
        return evaluateTracksToTransform(state.tracks, timeOnCurrentPath, molangContext)
    }

    private fun evaluateTracksToTransform(
        tracks: List<ICameraTrack>,
        time: Float,
        molangContext: Map<String, MoValue>
    ): CameraTransform? {
        var position: Vec3? = null
        var focus: LookTarget? = null
        var rotation: CameraRotation? = null

        for (track in tracks) {
            try {
                val result = track.evaluate(time, molangContext, runtime, origin)

                when (result) {
                    is CameraTransform -> {
                        if (position == null) position = result.position
                        if (focus == null) focus = result.focus
                        if (rotation == null) rotation = result.manualRotation
                    }
                    is Vec3 -> {
                        if (position == null) position = result
                    }
                    is LookTarget -> {
                        if (focus == null) focus = result
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CameraRunner] Error evaluating track ${track.trackType}", e)
            }
        }

        return if (position != null || focus != null) {
            CameraTransform(
                position ?: origin,
                focus,
                rotation
            )
        } else {
            null
        }
    }

    fun resolveFocus(focus: LookTarget): Vec3? {
        return when (focus) {
            is LookTarget.Entity -> {
                try {
                    val result = runtime.resolve(focus.selector, createMolangContext())
                    if (result is ObjectValue<*>) {
                        val entity = result.obj as? Entity
                        if (entity != null && !entity.isRemoved && entity.isAlive) {
                            entity.position()
                        } else {
                            Cobblemon.LOGGER.debug("[CameraRunner] Entity focus is invalid (dead/removed)")
                            null
                        }
                    } else null
                } catch (e: Exception) {
                    Cobblemon.LOGGER.error("[CameraRunner] Failed to resolve entity focus", e)
                    null
                }
            }
            is LookTarget.Point -> focus.position.toVec3(createMolangContext(), runtime = runtime)
        }
    }

    private fun createMolangContext(): Map<String, MoValue> {
        val evalContext = context.toMutableMap()

        runtime.environment.variable.map["time_on_current_path"] = DoubleValue(timeOnCurrentPath.toDouble())
        runtime.environment.variable.map["total_time"] = DoubleValue(totalTime.toDouble())
        runtime.environment.variable.map["runner_id"] = StringValue(id.toString())
        synchronized(customVariables) {
            customVariables.forEach { (name, value) ->
                runtime.environment.variable.map[name] = DoubleValue(value)
            }
        }

        evalContext["origin_x"] = DoubleValue(origin.x)
        evalContext["origin_y"] = DoubleValue(origin.y)
        evalContext["origin_z"] = DoubleValue(origin.z)

        return evalContext
    }

    fun setVariable(name: String, value: Double) {
        synchronized(customVariables) {
            customVariables[name] = value
        }
        runtime.environment.variable.map[name] = DoubleValue(value)
    }

    private fun storeCurrentProgress() {
        val state = currentState ?: return
        if (!state.rememberProgress) return
        val normalized = normalizeProgress(timeOnCurrentPath, state)
        pathProgress[state.stateId] = normalized
    }

    private fun bookmarkCurrentProgress() {
        val state = currentState ?: return
        if (!state.rememberProgress) return
        val normalized = normalizeProgress(timeOnCurrentPath, state)
        pathProgress[state.stateId] = normalized
    }

    private fun normalizeProgress(progress: Float, state: CameraState): Float {
        val duration = calculateStateDuration(state)
        return if (duration > 0f) {
            (progress / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}

