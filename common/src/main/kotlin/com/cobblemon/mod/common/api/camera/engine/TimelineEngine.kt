/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.engine

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.*
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.camera.track.EventTrack
import com.cobblemon.mod.common.client.camera.CameraEasing
import com.cobblemon.mod.common.client.camera.CameraInterpolationUtils
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.resolveBoolean
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe timeline execution engine.
 *
 * Uses immutable state snapshots for thread safety. MoLang evaluation is NOT
 * thread-safe - callers must ensure tick() is called from the same thread.
 *
 * @param id Unique identifier for this engine instance
 * @param timeline The timeline to execute
 * @param origin The world position origin for relative coordinates
 * @param runtime The MoLang runtime for expression evaluation
 * @param providedContext Initial context values (parameter values) to inject into MoLang
 */
class TimelineEngine(
    val id: UUID,
    private val timeline: Timeline,
    val origin: Vec3,
    private val runtime: MoLangRuntime,
    private val providedContext: Map<String, MoValue> = emptyMap()
) {
    @Volatile
    private var state: ExecutionState = ExecutionState.Stopped

    private var previousExecutionState: ExecutionState.Playing? = null

    private var instantTransitionOccurred = false

    private val customVariables = ConcurrentHashMap<String, Double>()

    private val timeOnCurrentPathValue = DoubleValue(0.0)
    private val totalTimeValue = DoubleValue(0.0)
    private val runnerIdValue = StringValue(id.toString())

    fun start(): Boolean {
        if (state !is ExecutionState.Stopped) {
            Cobblemon.LOGGER.warn("[TimelineEngine] Cannot start: already active (id=$id)")
            return false
        }

        // Validate timeline structure
        val errors = timeline.validate()
        if (errors.any { it.severity == ValidationError.Severity.ERROR }) {
            Cobblemon.LOGGER.error("[TimelineEngine] Timeline '${timeline.id}' validation failed: ${errors.joinToString { it.toString() }}")
            return false
        }

        // Validate and inject parameters
        if (!validateAndInjectParameters()) {
            return false
        }

        // Get start state
        val startState = timeline.getState(timeline.startStateId)
        if (startState == null) {
            Cobblemon.LOGGER.error("[TimelineEngine] Start state '${timeline.startStateId}' not found in timeline '${timeline.id}'")
            return false
        }

        // Initialize state
        state = ExecutionState.Playing(
            currentState = startState,
            stateTime = 0f,
            totalTime = 0f,
            lastTickNanos = System.nanoTime(),
            transitionState = TransitionState.None,
            progressMemory = emptyMap(),
            executedEvents = emptySet()
        )

        Cobblemon.LOGGER.info("[TimelineEngine] Started timeline '${timeline.id}' in state '${startState.id}'")
        return true
    }

    /**
     * Validates that all required parameters are provided and injects parameters
     * (including defaults) into the MoLang runtime.
     */
    private fun validateAndInjectParameters(): Boolean {
        if (timeline.parameters.isEmpty()) return true

        // Get provided variable names from context
        val providedVarNames = providedContext.keys

        // Check for missing required parameters
        val missingParams = timeline.validateParameters(providedVarNames)
        if (missingParams.isNotEmpty()) {
            Cobblemon.LOGGER.error("[TimelineEngine] Timeline '${timeline.id}' missing required parameters: ${missingParams.joinToString(", ")}")
            return false
        }

        // Inject all provided context values into runtime
        providedContext.forEach { (name, value) ->
            runtime.environment.variable.map[name] = value
        }

        // Inject defaults for any optional parameters not provided
        for ((paramName, definition) in timeline.parameters) {
            val default = definition.default ?: continue

            when (default) {
                is ParameterDefault.NumberDefault -> {
                    if (paramName !in providedVarNames) {
                        runtime.environment.variable.map[paramName] = DoubleValue(default.value)
                        Cobblemon.LOGGER.debug("[TimelineEngine] Using default for parameter '$paramName': ${default.value}")
                    }
                }
                is ParameterDefault.Vec3Default -> {
                    val varNames = definition.getVariableNames(paramName)
                    val xVar = varNames[0]
                    val yVar = varNames[1]
                    val zVar = varNames[2]

                    if (xVar !in providedVarNames) {
                        runtime.environment.variable.map[xVar] = DoubleValue(default.x)
                    }
                    if (yVar !in providedVarNames) {
                        runtime.environment.variable.map[yVar] = DoubleValue(default.y)
                    }
                    if (zVar !in providedVarNames) {
                        runtime.environment.variable.map[zVar] = DoubleValue(default.z)
                    }

                    if (xVar !in providedVarNames || yVar !in providedVarNames || zVar !in providedVarNames) {
                        Cobblemon.LOGGER.debug("[TimelineEngine] Using default for parameter '$paramName': (${default.x}, ${default.y}, ${default.z})")
                    }
                }
            }
        }

        return true
    }

    fun tick(): CameraTransform? {
        val current = state
        if (current !is ExecutionState.Playing) {
            return null
        }

        previousExecutionState = current
        instantTransitionOccurred = false

        val nowNanos = System.nanoTime()
        val deltaNanos = nowNanos - current.lastTickNanos
        val deltaSeconds = (deltaNanos / 1_000_000_000.0).toFloat()
            .coerceIn(0f, 0.5f)

        // Advance time
        val newStateTime = current.stateTime + deltaSeconds * current.currentState.speed
        val newTotalTime = current.totalTime + deltaSeconds

        // Check if we need to transition
        val transitionResult = checkTransitions(current, newStateTime, newTotalTime)
        if (transitionResult is TransitionCheckResult.TransitionNow) {
            // Transition to new state
            val targetState = timeline.getState(transitionResult.targetStateId)
                ?: run {
                    Cobblemon.LOGGER.error("[TimelineEngine] Transition target state '${transitionResult.targetStateId}' not found")
                    return null
                }

            state = when (transitionResult.type) {
                TransitionType.INSTANT -> {
                    instantTransitionOccurred = true

                    // Get saved state snapshot for target state (if remember progress)
                    val savedSnapshot = if (targetState.rememberProgress) {
                        current.progressMemory[targetState.id]
                    } else null

                    val restoredTime = savedSnapshot?.normalizedProgress?.let {
                        it * targetState.getEffectiveDuration()
                    } ?: 0f

                    // Restore focus from snapshot, or null if not remembering
                    val restoredFocus = savedSnapshot?.lastFocus

                    // Save current state with both progress and focus
                    val currentSnapshot = StateSnapshot(
                        normalizedProgress = normalizeProgress(current.stateTime, current.currentState),
                        lastFocus = current.lastEvaluatedFocus
                    )

                    ExecutionState.Playing(
                        currentState = targetState,
                        stateTime = restoredTime,
                        totalTime = newTotalTime,
                        lastTickNanos = nowNanos,
                        transitionState = TransitionState.None,
                        progressMemory = current.progressMemory + (current.currentState.id to currentSnapshot),
                        executedEvents = if (targetState.rememberProgress) current.executedEvents else emptySet(),
                        lastEvaluatedFocus = restoredFocus
                    )
                }
                TransitionType.BLEND -> {
                    // Start blending
                    val currentTransform = evaluateState(current.currentState, current.stateTime)
                    ExecutionState.Playing(
                        currentState = current.currentState,
                        stateTime = newStateTime,
                        totalTime = newTotalTime,
                        lastTickNanos = nowNanos,
                        transitionState = TransitionState.Blending(
                            targetState = targetState,
                            startTime = newTotalTime,
                            duration = transitionResult.blendDuration,
                            startTransform = currentTransform
                        ),
                        progressMemory = current.progressMemory,
                        executedEvents = current.executedEvents
                    )
                }
            }
        } else {
            // No transition - update time and check loop
            val effectiveDuration = current.currentState.getEffectiveDuration()
            val (finalStateTime, finalEvents) = if (current.currentState.isLooping && effectiveDuration > 0f && newStateTime >= effectiveDuration) {
                // Loop back
                newStateTime % effectiveDuration to emptySet<EventKey>()  // Clear executed events on loop
            } else {
                // Clamp at end for non-looping states
                val clampedTime = if (!current.currentState.isLooping && effectiveDuration > 0f) {
                    newStateTime.coerceAtMost(effectiveDuration)
                } else {
                    newStateTime
                }
                clampedTime to current.executedEvents
            }

            state = current.copy(
                stateTime = finalStateTime,
                totalTime = newTotalTime,
                lastTickNanos = nowNanos,
                executedEvents = finalEvents
            )
        }

        // Update blending if active
        val updatedState = state
        if (updatedState is ExecutionState.Playing && updatedState.transitionState is TransitionState.Blending) {
            val blend = updatedState.transitionState
            val blendProgress = if (blend.duration > 0f) {
                ((updatedState.totalTime - blend.startTime) / blend.duration).coerceIn(0f, 1f)
            } else 1f

            if (blendProgress >= 1f) {
                // Blend complete - switch to target state
                val savedSnapshot = updatedState.progressMemory[blend.targetState.id]
                val restoredTime = if (blend.targetState.rememberProgress && savedSnapshot != null) {
                    savedSnapshot.normalizedProgress * blend.targetState.getEffectiveDuration()
                } else 0f

                // Save current state snapshot
                val currentSnapshot = StateSnapshot(
                    normalizedProgress = normalizeProgress(updatedState.stateTime, updatedState.currentState),
                    lastFocus = null // Blend doesn't track focus (it interpolates instead)
                )

                state = ExecutionState.Playing(
                    currentState = blend.targetState,
                    stateTime = restoredTime,
                    totalTime = updatedState.totalTime,
                    lastTickNanos = nowNanos,
                    transitionState = TransitionState.None,
                    progressMemory = updatedState.progressMemory + (updatedState.currentState.id to currentSnapshot),
                    executedEvents = if (blend.targetState.rememberProgress) updatedState.executedEvents else emptySet()
                )
            }
        }

        // Execute events
        executeEvents()

        // Calculate current transform
        return calculateTransform()
    }

    fun stop() {
        state = ExecutionState.Stopped
        customVariables.clear()

        // Clear MoLang runtime to prevent memory leaks
        runtime.environment.variable.map.clear()
        runtime.environment.query.functions.clear()
    }

    fun setVariable(name: String, value: Double) {
        customVariables[name] = value
    }
    
    /** Triggers an instant transition on the next update (no client-side interpolation). */
    fun triggerInstantTransition() {
        instantTransitionOccurred = true
    }

    fun isActive(): Boolean = state is ExecutionState.Playing

    fun getCurrentStateId(): String? = (state as? ExecutionState.Playing)?.currentState?.id

    fun consumeInstantTransitionFlag(): Boolean {
        val result = instantTransitionOccurred
        instantTransitionOccurred = false
        return result
    }

    private fun checkTransitions(
        current: ExecutionState.Playing,
        newStateTime: Float,
        newTotalTime: Float
    ): TransitionCheckResult {
        // Don't check transitions while already transitioning
        if (current.transitionState !is TransitionState.None) {
            return TransitionCheckResult.NoTransition
        }

        val context = createMoLangContext(current.currentState, newStateTime, newTotalTime)

        for (transition in timeline.getTransitionsFrom(current.currentState.id)) {
            try {
                val shouldTransition = runtime.resolveBoolean(transition.condition, context)
                if (shouldTransition) {
                    return TransitionCheckResult.TransitionNow(
                        targetStateId = transition.targetStateId,
                        type = transition.type,
                        blendDuration = transition.blendDuration
                    )
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[TimelineEngine] Error evaluating transition condition: ${e.message}")
            }
        }

        return TransitionCheckResult.NoTransition
    }

    private fun executeEvents() {
        val current = state as? ExecutionState.Playing ?: return
        val previousState = previousExecutionState ?: return

        val context = createMoLangContext(current.currentState, current.stateTime, current.totalTime)
        val previousTime = previousState.stateTime
        val currentTime = current.stateTime

        current.currentState.tracks.filterIsInstance<EventTrack>().forEach { track ->
            track.events.forEach { timedEvent ->
                val eventKey = EventKey(current.currentState.id, timedEvent.eventId, timedEvent.time.toInt())

                if (eventKey in current.executedEvents) return@forEach

                val crossedEvent = if (previousTime <= currentTime) {
                    timedEvent.time in previousTime..currentTime
                } else {
                    timedEvent.time <= currentTime
                }

                if (crossedEvent) {
                    try {
                        runtime.resolve(timedEvent.expression, context)

                        state = current.copy(
                            executedEvents = current.executedEvents + eventKey
                        )

                        if (timedEvent.eventId != null) {
                            Cobblemon.LOGGER.debug("[TimelineEngine] Executed event '${timedEvent.eventId}' at time ${timedEvent.time}s")
                        }
                    } catch (e: Exception) {
                        Cobblemon.LOGGER.error("[TimelineEngine] Failed to execute event: ${e.message}")
                    }
                }
            }
        }
    }

    private fun calculateTransform(): CameraTransform {
        val current = state as? ExecutionState.Playing ?: return CameraTransform(origin, null, null)

        return when (val transition = current.transitionState) {
            is TransitionState.None -> {
                evaluateState(current.currentState, current.stateTime)
            }
            is TransitionState.Blending -> {
                val targetTransform = evaluateState(transition.targetState, 0f)
                val progress = if (transition.duration > 0f) {
                    ((current.totalTime - transition.startTime) / transition.duration).coerceIn(0f, 1f)
                } else 1f

                val easedProgress = CameraEasing.easeOutQuad(progress)

                blendTransforms(transition.startTransform, targetTransform, easedProgress)
            }
        }
    }

    private fun evaluateState(state: State, time: Float): CameraTransform {
        val context = createMoLangContext(state, time, (this.state as? ExecutionState.Playing)?.totalTime ?: 0f)

        var position: Vec3? = null
        var focus: CameraFocus? = null
        var rotation: CameraRotation? = null

        // Evaluate all tracks
        for (track in state.tracks) {
            val outputs = track.evaluate(time, context, runtime, origin)

            for (output in outputs) {
                when (output) {
                    is TrackOutput.Position -> {
                        if (position == null) position = output.position
                    }
                    is TrackOutput.Focus -> {
                        if (focus == null) focus = output.target
                    }
                    is TrackOutput.Rotation -> {
                        if (rotation == null) rotation = output.rotation
                    }
                    is TrackOutput.Event -> {
                        // Events handled separately
                    }
                }
            }
        }

        return CameraTransform(
            position = position ?: origin,
            focus = focus,
            rotation = rotation
        )
    }

    private fun blendTransforms(
        from: CameraTransform,
        to: CameraTransform,
        progress: Float
    ): CameraTransform {
        val blendedPosition = CameraInterpolationUtils.lerpPosition(from.position, to.position, progress)

        val blendedRotation = if (from.rotation != null && to.rotation != null) {
            CameraInterpolationUtils.slerpRotation(from.rotation, to.rotation, progress)
        } else {
            to.rotation ?: from.rotation
        }

        return CameraTransform(
            position = blendedPosition,
            focus = to.focus ?: from.focus,
            rotation = blendedRotation
        )
    }

    private fun createMoLangContext(
        state: State,
        stateTime: Float,
        totalTime: Float
    ): Map<String, MoValue> {
        // Update reusable values
        timeOnCurrentPathValue.value = stateTime.toDouble()
        totalTimeValue.value = totalTime.toDouble()

        // Set in runtime environment
        runtime.environment.variable.apply {
            map["time_on_current_path"] = timeOnCurrentPathValue
            map["total_time"] = totalTimeValue
            map["runner_id"] = runnerIdValue

            // Add custom variables
            customVariables.forEach { (name, value) ->
                map[name] = DoubleValue(value)
            }
        }

        return emptyMap()  // Context passed via runtime.environment
    }

    private fun normalizeProgress(time: Float, state: State): Float {
        val duration = state.getEffectiveDuration()
        return if (duration > 0f) {
            (time / duration).coerceIn(0f, 1f)
        } else 0f
    }
}

data class CameraTransform(
    val position: Vec3,
    val focus: CameraFocus?,
    val rotation: CameraRotation?
)

private sealed class ExecutionState {
    object Stopped : ExecutionState()

    data class Playing(
        val currentState: State,
        val stateTime: Float,
        val totalTime: Float,
        val lastTickNanos: Long,
        val transitionState: TransitionState,
        val progressMemory: Map<String, StateSnapshot>,
        val executedEvents: Set<EventKey>,
        val lastEvaluatedFocus: CameraFocus? = null
    ) : ExecutionState()
}

private sealed class TransitionState {
    object None : TransitionState()

    data class Blending(
        val targetState: State,
        val startTime: Float,
        val duration: Float,
        val startTransform: CameraTransform
    ) : TransitionState()
}

private sealed class TransitionCheckResult {
    object NoTransition : TransitionCheckResult()

    data class TransitionNow(
        val targetStateId: String,
        val type: TransitionType,
        val blendDuration: Float
    ) : TransitionCheckResult()
}

private data class EventKey(
    val stateId: String,
    val eventId: String?,
    val timeMillis: Int
)

private data class StateSnapshot(
    val normalizedProgress: Float,
    val lastFocus: CameraFocus?
)
