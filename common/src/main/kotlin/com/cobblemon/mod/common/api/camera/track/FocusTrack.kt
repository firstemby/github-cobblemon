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
import com.cobblemon.mod.common.api.tween.EasingRegistry
import com.cobblemon.mod.common.api.tween.Interpolator
import com.cobblemon.mod.common.api.tween.Keyframe
import com.cobblemon.mod.common.api.camera.model.*
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Track that animates camera look-at target over time.
 * Point targets interpolate smoothly; entity targets switch instantly.
 */
data class FocusTrack(
    val keyframes: List<FocusKeyframe>,
    val defaultEasing: String = "linear"
) : CameraTrack {
    override val trackType = "focus"

    override val duration: Float
        get() = keyframes.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput> {
        if (keyframes.isEmpty()) return emptyList()

        val segment = findFocusSegment(time) ?: return emptyList()

        val focus = when {
            // Both are Point targets - interpolate
            segment.start is CameraFocus.Point && segment.end is CameraFocus.Point -> {
                val startPos = segment.start.position.toVec3(context, runtime)
                val endPos = segment.end.position.toVec3(context, runtime)

                // Interpolate positions
                val easedProgress = EasingRegistry.getEasingFunction(segment.easing)(segment.progress)
                val interpolatedPos = Vec3(
                    Mth.lerp(easedProgress.toDouble(), startPos.x, endPos.x),
                    Mth.lerp(easedProgress.toDouble(), startPos.y, endPos.y),
                    Mth.lerp(easedProgress.toDouble(), startPos.z, endPos.z)
                )

                CameraFocus.Point(
                    PositionExpression(
                        CoordinateValue.Static(interpolatedPos.x),
                        CoordinateValue.Static(interpolatedPos.y),
                        CoordinateValue.Static(interpolatedPos.z)
                    )
                )
            }
            // Entity targets or mixed - use instant switching
            else -> {
                if (segment.progress < 0.5f) segment.start else segment.end
            }
        }

        return listOf(TrackOutput.Focus(focus))
    }

    private fun findFocusSegment(time: Float): FocusSegment? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) {
            val kf = keyframes[0]
            return FocusSegment(kf.target, kf.target, kf.time, kf.time, 1f, defaultEasing)
        }

        for (i in 0 until keyframes.size - 1) {
            val start = keyframes[i]
            val end = keyframes[i + 1]

            if (time <= end.time) {
                val duration = end.time - start.time
                val progress = if (duration > 0f) {
                    ((time - start.time) / duration).coerceIn(0f, 1f)
                } else 1f

                val easing = end.easing ?: defaultEasing

                return FocusSegment(start.target, end.target, start.time, end.time, progress, easing)
            }
        }

        val last = keyframes.last()
        return FocusSegment(last.target, last.target, last.time, last.time, 1f, defaultEasing)
    }

    init {
        require(keyframes.isNotEmpty()) { "FocusTrack must have at least one keyframe" }
        require(keyframes.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "FocusTrack keyframes must be sorted by time"
        }
    }

    private data class FocusSegment(
        val start: CameraFocus,
        val end: CameraFocus,
        val startTime: Float,
        val endTime: Float,
        val progress: Float,
        val easing: String
    )
}

data class FocusKeyframe(
    val time: Float,
    val target: CameraFocus,
    val easing: String? = null
) {
    init {
        require(time >= 0f) { "Keyframe time must be non-negative, got $time" }
    }
}
