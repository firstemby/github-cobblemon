/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.molang.ExpressionLike

/**
 * Immutable timeline definition - a state machine of camera animations with transitions.
 *
 * Timelines can declare required and optional parameters that must be provided when
 * starting the camera. Parameters are injected into the MoLang runtime as variables,
 * allowing tracks to use dynamic values like `variable.arena_center_x`.
 *
 * @property id Unique identifier for this timeline
 * @property startStateId The ID of the initial state when the timeline starts
 * @property parameters Map of parameter names to their definitions. Parameters without
 *                      defaults are required and must be provided when starting the camera.
 * @property states Map of state IDs to state definitions
 * @property transitions List of transitions between states
 */
data class Timeline(
    val id: String,
    val startStateId: String,
    val parameters: Map<String, ParameterDefinition> = emptyMap(),
    val states: Map<String, State>,
    val transitions: List<Transition>
) {
    fun getState(stateId: String): State? = states[stateId]

    fun getTransitionsFrom(stateId: String): List<Transition> =
        transitions.filter { it.sourceStateId == stateId }

    /**
     * Gets the names of all required parameters (those without defaults).
     */
    fun getRequiredParameterNames(): Set<String> =
        parameters.filter { it.value.isRequired }.keys

    /**
     * Gets all variable names that this timeline's parameters will inject.
     * For NUMBER parameters, returns the parameter name.
     * For VEC3 parameters, returns name_x, name_y, name_z.
     */
    fun getAllParameterVariableNames(): Set<String> =
        parameters.flatMap { (name, def) -> def.getVariableNames(name) }.toSet()

    /**
     * Validates that all required parameters are provided in the given context.
     *
     * @param providedVariables Set of variable names provided in the context
     * @return List of missing parameter names, empty if all required params are present
     */
    fun validateParameters(providedVariables: Set<String>): List<String> {
        val missingParams = mutableListOf<String>()

        for ((paramName, definition) in parameters) {
            if (!definition.isRequired) continue

            val requiredVars = definition.getVariableNames(paramName)
            val allProvided = requiredVars.all { it in providedVariables }

            if (!allProvided) {
                missingParams.add(paramName)
            }
        }

        return missingParams
    }

    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Check start state exists
        if (startStateId !in states) {
            errors.add(ValidationError(
                path = "startStateId",
                message = "Start state '$startStateId' not found in states map"
            ))
        }

        // Check all transition states exist
        transitions.forEachIndexed { index, transition ->
            if (transition.sourceStateId !in states) {
                errors.add(ValidationError(
                    path = "transitions[$index].sourceStateId",
                    message = "Source state '${transition.sourceStateId}' not found"
                ))
            }
            if (transition.targetStateId !in states) {
                errors.add(ValidationError(
                    path = "transitions[$index].targetStateId",
                    message = "Target state '${transition.targetStateId}' not found"
                ))
            }
        }

        states.forEach { (stateId, state) ->
            if (state.tracks.isEmpty()) {
                errors.add(ValidationError(
                    path = "states[$stateId].tracks",
                    message = "State has no tracks defined",
                    severity = ValidationError.Severity.WARNING
                ))
            }
        }

        val reachableStates = findReachableStates()
        states.keys.forEach { stateId ->
            if (stateId !in reachableStates) {
                errors.add(ValidationError(
                    path = "states[$stateId]",
                    message = "State is unreachable from start state '$startStateId'",
                    severity = ValidationError.Severity.WARNING
                ))
            }
        }

        val cycles = detectCycles()
        if (cycles.isNotEmpty()) {
            errors.add(ValidationError(
                path = "transitions",
                message = "Detected transition cycles: ${cycles.joinToString(", ") { it.joinToString(" -> ") }}",
                severity = ValidationError.Severity.WARNING
            ))
        }

        return errors
    }

    private fun findReachableStates(): Set<String> {
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        if (startStateId in states) {
            queue.add(startStateId)
            reachable.add(startStateId)
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            transitions
                .filter { it.sourceStateId == current }
                .forEach { transition ->
                    if (transition.targetStateId !in reachable && transition.targetStateId in states) {
                        reachable.add(transition.targetStateId)
                        queue.add(transition.targetStateId)
                    }
                }
        }

        return reachable
    }

    private fun detectCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>): Boolean {
            if (node !in states) return false

            visited.add(node)
            recStack.add(node)
            path.add(node)

            for (transition in getTransitionsFrom(node)) {
                val target = transition.targetStateId
                if (target !in visited) {
                    if (dfs(target, path)) return true
                } else if (target in recStack) {
                    val cycleStart = path.indexOf(target)
                    if (cycleStart >= 0) {
                        cycles.add(path.subList(cycleStart, path.size) + target)
                    }
                }
            }

            path.removeAt(path.size - 1)
            recStack.remove(node)
            return false
        }

        states.keys.forEach { stateId ->
            if (stateId !in visited) {
                dfs(stateId, mutableListOf())
            }
        }

        return cycles
    }
}

/**
 * Immutable camera state definition - a collection of tracks that play together.
 */
data class State(
    val id: String,
    val tracks: List<CameraTrack>,
    val duration: Float? = null,
    val isLooping: Boolean = false,
    val speed: Float = 1.0f,
    val rememberProgress: Boolean = false,
    val renderPlayerBody: Boolean = true,
    val hiddenHudElements: Set<HudElement> = emptySet()
) {
    init {
        require(speed > 0f) { "State speed must be positive, got $speed" }
        duration?.let { d ->
            require(d >= 0f) { "State duration must be non-negative, got $d" }
        }
    }

    fun getEffectiveDuration(): Float {
        return duration ?: tracks.maxOfOrNull { it.duration } ?: 0f
    }
}

/**
 * Immutable transition definition - defines when and how to move from one state to another.
 */
data class Transition(
    val sourceStateId: String,
    val targetStateId: String,
    val condition: ExpressionLike,
    val type: TransitionType = TransitionType.BLEND,
    val blendDuration: Float = 0.4f
) {
    init {
        require(blendDuration >= 0f) { "Blend duration must be non-negative, got $blendDuration" }
    }
}

enum class TransitionType {
    INSTANT,
    BLEND
}
