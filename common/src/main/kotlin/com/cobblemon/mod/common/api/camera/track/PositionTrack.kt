/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.track

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.camera.interpolation.*
import com.cobblemon.mod.common.api.camera.model.*
import net.minecraft.world.phys.Vec3

/**
 * Track that animates camera position over time using keyframes.
 */
data class PositionTrack(
    val keyframes: List<PositionKeyframe>,
    val interpolation: InterpolationMethod = InterpolationMethod.LINEAR,
    val defaultEasing: String = "linear",
    val isRelative: Boolean = false
) : CameraTrack {
    override val trackType = "position"

    override val duration: Float
        get() = keyframes.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput> {
        if (keyframes.isEmpty()) return emptyList()

        // Convert keyframes to interpolatable values
        val interpolatableKeyframes = keyframes.map { kf ->
            val vec = kf.position.toVec3(context, runtime, origin, isRelative)
            Keyframe(kf.time, vec.asInterpolatable())
        }

        // Interpolate using the new system
        val interpolated = Interpolator.interpolate(
            keyframes = interpolatableKeyframes,
            time = time,
            method = interpolation,
            defaultEasing = defaultEasing,
            getEasing = { value ->
                // Find original keyframe to get easing
                val index = interpolatableKeyframes.indexOfFirst { it.value == value }
                keyframes.getOrNull(index)?.easing
            }
        )

        return if (interpolated != null) {
            listOf(TrackOutput.Position(interpolated.vec))
        } else {
            emptyList()
        }
    }

    init {
        require(keyframes.isNotEmpty()) { "PositionTrack must have at least one keyframe" }
        require(keyframes.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "PositionTrack keyframes must be sorted by time"
        }
    }
}

/**
 * Position keyframe - a position at a specific time.
 */
data class PositionKeyframe(
    val time: Float,
    val position: PositionExpression,
    val easing: String? = null
) {
    init {
        require(time >= 0f) { "Keyframe time must be non-negative, got $time" }
    }
}
