/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.builders

import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.client.camera.*

class CameraTimelineBuilder(private val timelineId: String) {
    private var startStateId: String? = null
    private val states = mutableMapOf<String, CameraState>()
    private val transitions = mutableListOf<CameraTransition>()

    fun startState(stateId: String): CameraTimelineBuilder {
        this.startStateId = stateId
        return this
    }

    fun state(stateId: String, block: StateBuilder.() -> Unit): CameraTimelineBuilder {
        val builder = StateBuilder(stateId)
        builder.block()
        states[stateId] = builder.build()
        return this
    }

    fun state(state: CameraState): CameraTimelineBuilder {
        states[state.stateId] = state
        return this
    }

    fun transition(block: TransitionBuilder.() -> Unit): CameraTimelineBuilder {
        val builder = TransitionBuilder()
        builder.block()
        transitions.add(builder.build())
        return this
    }

    fun transition(transition: CameraTransition): CameraTimelineBuilder {
        transitions.add(transition)
        return this
    }

    fun build(): CameraTimeline {
        requireNotNull(startStateId) {
            "Timeline '$timelineId' must have a start state"
        }

        require(states.isNotEmpty()) {
            "Timeline '$timelineId' must have at least one state"
        }

        require(states.containsKey(startStateId)) {
            "Timeline '$timelineId' start state '$startStateId' does not exist in defined states"
        }

        return CameraTimeline(
            timelineId = timelineId,
            startStateId = startStateId!!,
            states = states.toMap(),
            transitions = transitions.toList()
        )
    }

    class StateBuilder(private val stateId: String) {
        private val tracks = mutableListOf<ICameraTrack>()
        private var isLooping: Boolean = false
        private var speed: Float = 1.0f
        private var positionInterpolation: String = "linear"
        private var rotationInterpolation: String = "spherical"
        private var rememberProgress: Boolean = false
        private val events = mutableListOf<CameraEvent>()

        fun track(track: ICameraTrack) {
            tracks.add(track)
        }

        fun tracks(vararg tracks: ICameraTrack) {
            this.tracks.addAll(tracks)
        }

        fun event(time: Float, expression: ExpressionLike, eventId: String? = null) {
            events.add(CameraEvent(time, expression, eventId))
        }

        fun event(event: CameraEvent) {
            events.add(event)
        }

        fun looping(looping: Boolean) {
            this.isLooping = looping
        }

        fun speed(speed: Float) {
            this.speed = speed
        }

        fun positionInterpolation(mode: String) {
            this.positionInterpolation = mode
        }

        fun rotationInterpolation(mode: String) {
            this.rotationInterpolation = mode
        }

        fun rememberProgress(remember: Boolean) {
            this.rememberProgress = remember
        }

        fun build(): CameraState {
            require(tracks.isNotEmpty()) {
                "State '$stateId' must have at least one track. Use track(), tracks(), or path() to add content."
            }

            return CameraState(
                stateId = stateId,
                tracks = tracks.toList(),
                isLooping = isLooping,
                speed = speed,
                interpolation = CameraInterpolation(
                    position = positionInterpolation,
                    rotation = rotationInterpolation
                ),
                rememberProgress = rememberProgress,
                events = events.toList()
            )
        }
    }

    class TransitionBuilder {
        private var sourceStateId: String? = null
        private var targetStateId: String? = null
        private var condition: ExpressionLike? = null
        private var blendDuration: Float = 0.4f
        private var transitionType: TransitionType = TransitionType.BLEND

        fun from(stateId: String) {
            this.sourceStateId = stateId
        }

        fun to(stateId: String) {
            this.targetStateId = stateId
        }

        fun condition(expression: ExpressionLike) {
            this.condition = expression
        }

        fun blendDuration(seconds: Float) {
            this.blendDuration = seconds
        }

        fun type(type: TransitionType) {
            this.transitionType = type
        }

        fun blend() {
            this.transitionType = TransitionType.BLEND
        }

        fun instant() {
            this.transitionType = TransitionType.INSTANT
        }

        fun build(): CameraTransition {
            requireNotNull(sourceStateId) {
                "Transition must have a source state"
            }

            requireNotNull(targetStateId) {
                "Transition must have a target state"
            }

            requireNotNull(condition) {
                "Transition must have a condition"
            }

            return CameraTransition(
                sourceStateId = sourceStateId!!,
                targetStateId = targetStateId!!,
                condition = condition!!,
                blendDuration = blendDuration,
                transitionType = transitionType
            )
        }
    }
}
