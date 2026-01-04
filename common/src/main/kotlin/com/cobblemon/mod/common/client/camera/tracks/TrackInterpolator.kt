/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera.tracks

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.client.camera.*
import com.cobblemon.mod.common.util.mainThreadRuntime
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.pow

/**
 * Interpolator for camera tracks using absolute time-based keyframes.
 * This is the new system that replaces the legacy MotionPath interpolation.
 *
 * Unlike the old system which used `timeToReach` (relative deltas), this works
 * with `time` (absolute timestamps), which is simpler and more intuitive.
 *
 * @author Terrainwax
 * @since January 2025
 */
object TrackInterpolator {

    /**
     * Represents a segment between two keyframes for interpolation.
     */
    data class TimeSegment<T>(
        val startValue: T,
        val endValue: T,
        val startTime: Float,
        val endTime: Float,
        val progress: Float, // 0-1 progress through this segment
        val easing: String
    ) {
        val duration: Float get() = endTime - startTime
    }

    /**
     * Finds the keyframe segment that contains the given time.
     */
    fun <T> findSegment(
        keyframes: List<Pair<Float, T>>, // Pairs of (time, value)
        time: Float,
        defaultEasing: String = "linear",
        getEasing: ((T) -> String?)? = null
    ): TimeSegment<T>? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) {
            val (t, v) = keyframes[0]
            return TimeSegment(v, v, t, t, 1f, defaultEasing)
        }

        // Find the segment containing this time
        for (i in 0 until keyframes.size - 1) {
            val (startTime, startValue) = keyframes[i]
            val (endTime, endValue) = keyframes[i + 1]

            if (time <= endTime) {
                val duration = endTime - startTime
                val progress = if (duration > 0) (time - startTime) / duration else 1f
                val easing = getEasing?.invoke(endValue) ?: defaultEasing
                return TimeSegment(startValue, endValue, startTime, endTime, progress.coerceIn(0f, 1f), easing)
            }
        }

        // Past the end - return last keyframe
        val (t, v) = keyframes.last()
        return TimeSegment(v, v, t, t, 1f, defaultEasing)
    }

    /**
     * Interpolates position between keyframes using the specified interpolation type.
     */
    fun interpolatePosition(
        keyframes: List<PositionKeyframe>,
        time: Float,
        interpolationType: PathInterpolationType,
        defaultEasing: String,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime = mainThreadRuntime,
        origin: Vec3 = Vec3.ZERO,
        isRelative: Boolean = false
    ): Vec3? {
        if (keyframes.isEmpty()) return null

        // Convert to time-value pairs
        val timeValuePairs = keyframes.map { kf ->
            kf.time to kf.position.toVec3(context, origin, isRelative, runtime)
        }

        return when (interpolationType) {
            PathInterpolationType.LINEAR -> interpolateLinear(timeValuePairs, time, keyframes, defaultEasing)
            PathInterpolationType.BEZIER -> interpolateBezier(timeValuePairs, time, keyframes, defaultEasing)
            PathInterpolationType.HERMITE -> interpolateHermite(timeValuePairs, time, keyframes, defaultEasing)
            PathInterpolationType.CUBIC_BEZIER -> interpolateCubicBezier(timeValuePairs, time, keyframes, defaultEasing)
        }
    }

    /**
     * Linear interpolation between two positions.
     */
    private fun interpolateLinear(
        timeValuePairs: List<Pair<Float, Vec3>>,
        time: Float,
        keyframes: List<PositionKeyframe>,
        defaultEasing: String
    ): Vec3? {
        val segment = findSegment(timeValuePairs, time, defaultEasing) {
            val idx = timeValuePairs.indexOfFirst { it.second == it }
            keyframes.getOrNull(idx)?.easing
        } ?: return null

        val easedProgress = CameraEasing.getEasingFunction(segment.easing)(segment.progress)
        return lerpVec3(segment.startValue, segment.endValue, easedProgress)
    }

    /**
     * Bezier/Catmull-Rom spline interpolation (smooth curves through all points).
     */
    private fun interpolateBezier(
        timeValuePairs: List<Pair<Float, Vec3>>,
        time: Float,
        keyframes: List<PositionKeyframe>,
        defaultEasing: String
    ): Vec3? {
        if (timeValuePairs.size < 2) return timeValuePairs.firstOrNull()?.second

        // Find current segment
        val segmentIndex = timeValuePairs.indexOfFirst { it.first > time }.let {
            if (it <= 0) timeValuePairs.size - 1 else it
        } - 1

        if (segmentIndex < 0) return timeValuePairs.first().second
        if (segmentIndex >= timeValuePairs.size - 1) return timeValuePairs.last().second

        val p0 = if (segmentIndex > 0) timeValuePairs[segmentIndex - 1].second else timeValuePairs[segmentIndex].second
        val p1 = timeValuePairs[segmentIndex].second
        val p2 = timeValuePairs[segmentIndex + 1].second
        val p3 = if (segmentIndex < timeValuePairs.size - 2) timeValuePairs[segmentIndex + 2].second else p2

        val t1 = timeValuePairs[segmentIndex].first
        val t2 = timeValuePairs[segmentIndex + 1].first
        val duration = t2 - t1
        val localProgress = if (duration > 0) (time - t1) / duration else 0f

        val easing = keyframes[segmentIndex + 1].easing ?: defaultEasing
        val t = CameraEasing.getEasingFunction(easing)(localProgress.coerceIn(0f, 1f))

        return catmullRom(p0, p1, p2, p3, t)
    }

    /**
     * Hermite spline interpolation.
     */
    private fun interpolateHermite(
        timeValuePairs: List<Pair<Float, Vec3>>,
        time: Float,
        keyframes: List<PositionKeyframe>,
        defaultEasing: String
    ): Vec3? {
        val segment = findSegment(timeValuePairs, time, defaultEasing) {
            val idx = timeValuePairs.indexOfFirst { it.second == it }
            keyframes.getOrNull(idx)?.easing
        } ?: return null

        val easedProgress = CameraEasing.getEasingFunction(segment.easing)(segment.progress)

        // Calculate tangents based on neighboring points
        val idx = timeValuePairs.indexOfFirst { it.first == segment.startTime }
        val prevPos = if (idx > 0) timeValuePairs[idx - 1].second else segment.startValue
        val nextPos = if (idx < timeValuePairs.size - 2) timeValuePairs[idx + 2].second else segment.endValue

        val tangent1 = segment.endValue.subtract(prevPos).scale(0.5)
        val tangent2 = nextPos.subtract(segment.startValue).scale(0.5)

        return hermite(segment.startValue, segment.endValue, tangent1, tangent2, easedProgress)
    }

    /**
     * Cubic Bezier interpolation.
     */
    private fun interpolateCubicBezier(
        timeValuePairs: List<Pair<Float, Vec3>>,
        time: Float,
        keyframes: List<PositionKeyframe>,
        defaultEasing: String
    ): Vec3? {
        // For now, use same as Bezier - can be extended with control points later
        return interpolateBezier(timeValuePairs, time, keyframes, defaultEasing)
    }

    // === Math Utilities ===

    private fun lerpVec3(start: Vec3, end: Vec3, t: Float): Vec3 {
        return Vec3(
            Mth.lerp(t.toDouble(), start.x, end.x),
            Mth.lerp(t.toDouble(), start.y, end.y),
            Mth.lerp(t.toDouble(), start.z, end.z)
        )
    }

    private fun catmullRom(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t

        val x = 0.5 * (
            (2 * p1.x) +
            (-p0.x + p2.x) * t +
            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
            (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
        )

        val y = 0.5 * (
            (2 * p1.y) +
            (-p0.y + p2.y) * t +
            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
            (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
        )

        val z = 0.5 * (
            (2 * p1.z) +
            (-p0.z + p2.z) * t +
            (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
            (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3
        )

        return Vec3(x, y, z)
    }

    private fun hermite(p0: Vec3, p1: Vec3, m0: Vec3, m1: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t

        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2

        return Vec3(
            h00 * p0.x + h10 * m0.x + h01 * p1.x + h11 * m1.x,
            h00 * p0.y + h10 * m0.y + h01 * p1.y + h11 * m1.y,
            h00 * p0.z + h10 * m0.z + h01 * p1.z + h11 * m1.z
        )
    }
}
