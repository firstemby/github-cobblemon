/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

object Interpolator {

    fun <T> interpolate(
        keyframes: List<Keyframe<T>>,
        time: Float,
        method: InterpolationMethod,
        defaultEasing: String = "linear",
        getEasing: ((T) -> String?)? = null
    ): T? where T : Interpolatable<T> {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) return keyframes[0].value

        return when (method) {
            InterpolationMethod.LINEAR -> interpolateLinear(keyframes, time, defaultEasing, getEasing)
            InterpolationMethod.BEZIER -> interpolateBezier(keyframes, time, defaultEasing, getEasing)
            InterpolationMethod.HERMITE -> interpolateHermite(keyframes, time, defaultEasing, getEasing)
            InterpolationMethod.CUBIC_BEZIER -> interpolateCubicBezier(keyframes, time, defaultEasing, getEasing)
        }
    }

    /**
     * @return Segment with normalized progress [0-1], or null if keyframes is empty
     */
    fun <T> findSegment(
        keyframes: List<Keyframe<T>>,
        time: Float,
        defaultEasing: String = "linear",
        getEasing: ((T) -> String?)? = null
    ): Segment<T>? {
        if (keyframes.isEmpty()) return null

        if (keyframes.size == 1) {
            val kf = keyframes[0]
            return Segment(
                startValue = kf.value,
                endValue = kf.value,
                startTime = kf.time,
                endTime = kf.time,
                progress = 1f,
                easing = defaultEasing
            )
        }

        for (i in 0 until keyframes.size - 1) {
            val start = keyframes[i]
            val end = keyframes[i + 1]

            if (time <= end.time) {
                val duration = end.time - start.time
                val progress = if (duration > 0f) {
                    ((time - start.time) / duration).coerceIn(0f, 1f)
                } else 1f

                val easing = getEasing?.invoke(end.value) ?: defaultEasing

                return Segment(
                    startValue = start.value,
                    endValue = end.value,
                    startTime = start.time,
                    endTime = end.time,
                    progress = progress,
                    easing = easing
                )
            }
        }

        val last = keyframes.last()
        return Segment(
            startValue = last.value,
            endValue = last.value,
            startTime = last.time,
            endTime = last.time,
            progress = 1f,
            easing = defaultEasing
        )
    }

    private fun <T> interpolateLinear(
        keyframes: List<Keyframe<T>>,
        time: Float,
        defaultEasing: String,
        getEasing: ((T) -> String?)?
    ): T where T : Interpolatable<T> {
        val segment = findSegment(keyframes, time, defaultEasing, getEasing)!!
        val easedProgress = EasingRegistry.getEasingFunction(segment.easing)(segment.progress)
        return segment.startValue.lerp(segment.endValue, easedProgress)
    }

    private fun <T> interpolateBezier(
        keyframes: List<Keyframe<T>>,
        time: Float,
        defaultEasing: String,
        getEasing: ((T) -> String?)?
    ): T where T : Interpolatable<T> {
        if (keyframes.size < 2) return keyframes.first().value

        val segmentIndex = keyframes.indexOfLast { it.time <= time }
            .coerceIn(0, keyframes.size - 2)

        val p0 = if (segmentIndex > 0) keyframes[segmentIndex - 1].value else keyframes[segmentIndex].value
        val p1 = keyframes[segmentIndex].value
        val p2 = keyframes[segmentIndex + 1].value
        val p3 = if (segmentIndex < keyframes.size - 2) keyframes[segmentIndex + 2].value else p2

        val t1 = keyframes[segmentIndex].time
        val t2 = keyframes[segmentIndex + 1].time
        val duration = t2 - t1
        val localProgress = if (duration > 0f) {
            ((time - t1) / duration).coerceIn(0f, 1f)
        } else 0f

        val easing = getEasing?.invoke(p2) ?: defaultEasing
        val t = EasingRegistry.getEasingFunction(easing)(localProgress)

        return catmullRom(p0, p1, p2, p3, t)
    }

    private fun <T> interpolateHermite(
        keyframes: List<Keyframe<T>>,
        time: Float,
        defaultEasing: String,
        getEasing: ((T) -> String?)?
    ): T where T : Interpolatable<T> {
        val segment = findSegment(keyframes, time, defaultEasing, getEasing)!!
        val easedProgress = EasingRegistry.getEasingFunction(segment.easing)(segment.progress)

        val segmentIndex = keyframes.indexOfFirst { it.time == segment.startTime }
        val prevValue = if (segmentIndex > 0) keyframes[segmentIndex - 1].value else segment.startValue
        val nextValue = if (segmentIndex < keyframes.size - 2) keyframes[segmentIndex + 2].value else segment.endValue

        val tangent1 = segment.endValue.subtract(prevValue).scale(0.5f)
        val tangent2 = nextValue.subtract(segment.startValue).scale(0.5f)

        return hermite(segment.startValue, segment.endValue, tangent1, tangent2, easedProgress)
    }

    private fun <T> interpolateCubicBezier(
        keyframes: List<Keyframe<T>>,
        time: Float,
        defaultEasing: String,
        getEasing: ((T) -> String?)?
    ): T where T : Interpolatable<T> {
        return interpolateBezier(keyframes, time, defaultEasing, getEasing)
    }

    private fun <T> catmullRom(p0: T, p1: T, p2: T, p3: T, t: Float): T where T : Interpolatable<T> {
        val t2 = t * t
        val t3 = t2 * t

        val c0 = -0.5f * t3 + 1.0f * t2 - 0.5f * t
        val c1 = 1.5f * t3 - 2.5f * t2 + 1.0f
        val c2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t
        val c3 = 0.5f * t3 - 0.5f * t2

        return p0.scale(c0)
            .add(p1.scale(c1))
            .add(p2.scale(c2))
            .add(p3.scale(c3))
    }

    private fun <T> hermite(p0: T, p1: T, m0: T, m1: T, t: Float): T where T : Interpolatable<T> {
        val t2 = t * t
        val t3 = t2 * t

        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2

        return p0.scale(h00)
            .add(m0.scale(h10))
            .add(p1.scale(h01))
            .add(m1.scale(h11))
    }
}
