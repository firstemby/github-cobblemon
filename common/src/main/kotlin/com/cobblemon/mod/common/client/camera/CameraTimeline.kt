/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.api.molang.ExpressionLike

data class CameraTimeline(
    val timelineId: String,
    val startStateId: String,
    val states: Map<String, CameraState>,
    val transitions: List<CameraTransition>
) {
    fun getState(stateId: String): CameraState? = states[stateId]

    fun getTransitionsFrom(stateId: String): List<CameraTransition> {
        return transitions.filter { it.sourceStateId == stateId }
    }
}

data class CameraState(
    val stateId: String,
    val tracks: List<ICameraTrack> = emptyList(),
    val isLooping: Boolean = false,
    val speed: Float = 1.0f,
    val interpolation: CameraInterpolation = CameraInterpolation(),
    val rememberProgress: Boolean = false,
    val renderPlayerBody: Boolean = true,
    val events: List<CameraEvent> = emptyList()
)

data class CameraEvent(
    val time: Float,
    val expression: ExpressionLike,
    val eventId: String? = null
)

data class CameraInterpolation(
    val position: String = "linear",
    val rotation: String = "spherical"
)

data class CameraTransition(
    val sourceStateId: String,
    val targetStateId: String,
    val condition: ExpressionLike,
    val blendDuration: Float = 0.4f,
    val transitionType: TransitionType = TransitionType.BLEND
)

enum class TransitionType {
    BLEND,
    INSTANT
}

