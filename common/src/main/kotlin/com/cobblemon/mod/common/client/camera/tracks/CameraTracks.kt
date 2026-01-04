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
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.client.camera.*
import com.cobblemon.mod.common.util.resolve
import net.minecraft.world.phys.Vec3

/**
 * Track that controls camera position over time using keyframes.
 *
 * Uses absolute time-based keyframes (not relative timeToReach like the old system).
 *
 * @property keyframes List of position keyframes
 * @property interpolationType How to interpolate between keyframes
 * @property easing Default easing function (can be overridden per keyframe)
 * @property isRelative Whether positions are relative to origin
 *
 * @author Terrainwax
 * @since January 2025
 */
data class PositionTrack(
    val keyframes: List<PositionKeyframe>,
    val interpolationType: PathInterpolationType = PathInterpolationType.LINEAR,
    val easing: String = "linear",
    val isRelative: Boolean = false
) : ICameraTrack {
    override val trackType = "position"

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): Vec3? {
        return TrackInterpolator.interpolatePosition(
            keyframes = keyframes,
            time = time,
            interpolationType = interpolationType,
            defaultEasing = easing,
            context = context,
            runtime = runtime,
            origin = origin,
            isRelative = isRelative
        )
    }
}

/**
 * Position keyframe for the position track.
 */
data class PositionKeyframe(
    val time: Float,
    val position: PositionExpression,
    val easing: String? = null
)

/**
 * Track that controls camera focus/look-at target over time.
 *
 * Interpolates smoothly between Point targets. Entity targets are switched instantly.
 *
 * @property keyframes List of focus keyframes
 * @property easing Easing function for interpolation
 *
 * @author Terrainwax
 * @since January 2025
 */
data class FocusTrack(
    val keyframes: List<FocusKeyframe>,
    val easing: String = "linear"
) : ICameraTrack {
    override val trackType = "focus"

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): LookTarget? {
        if (keyframes.isEmpty()) return null

        // Find the segment containing this time
        val segment = TrackInterpolator.findSegment(
            keyframes = keyframes.map { it.time to it.target },
            time = time,
            defaultEasing = easing,
            getEasing = { keyframes.find { kf -> kf.target == it }?.easing }
        ) ?: return null

        // Interpolate between Point targets, instant switch for Entity targets
        return when {
            segment.startValue is LookTarget.Point && segment.endValue is LookTarget.Point -> {
                val startPos = segment.startValue.position.toVec3(context, origin, false, runtime)
                val endPos = segment.endValue.position.toVec3(context, origin, false, runtime)

                val easedProgress = CameraEasing.getEasingFunction(segment.easing)(segment.progress)
                val interpolatedPos = Vec3(
                    net.minecraft.util.Mth.lerp(easedProgress.toDouble(), startPos.x, endPos.x),
                    net.minecraft.util.Mth.lerp(easedProgress.toDouble(), startPos.y, endPos.y),
                    net.minecraft.util.Mth.lerp(easedProgress.toDouble(), startPos.z, endPos.z)
                )

                LookTarget.Point(PositionExpression(
                    CoordinateValue.Static(interpolatedPos.x),
                    CoordinateValue.Static(interpolatedPos.y),
                    CoordinateValue.Static(interpolatedPos.z)
                ))
            }
            else -> {
                // Entity targets or mixed types - use the current target without interpolation
                if (segment.progress < 0.5f) segment.startValue else segment.endValue
            }
        }
    }
}

/**
 * Focus keyframe for the focus track.
 */
data class FocusKeyframe(
    val time: Float,
    val target: LookTarget,
    val easing: String? = null
)

/**
 * Track that executes MoLang expressions at specific times.
 *
 * @property events List of timed events
 *
 * @author Terrainwax
 * @since January 2025
 */
data class EventTrack(
    val events: List<TimedEvent>
) : ICameraTrack {
    override val trackType = "event"

    // Events are handled differently - they don't return a value,
    // but instead trigger side effects. The timeline runner handles execution.
    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TimedEvent> {
        // Return events that should trigger at this time
        // (Actual execution is handled by the timeline runner)
        return events.filter { it.time == time }
    }

    /**
     * Executes an event's MoLang expression.
     */
    fun executeEvent(
        event: TimedEvent,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime
    ) {
        try {
            val result = runtime.resolve(event.expression, context)
            if (event.eventId != null) {
                Cobblemon.LOGGER.debug("[EventTrack] Executed event '${event.eventId}' at time ${event.time}s, result: $result")
            }
        } catch (e: Exception) {
            val eventName = event.eventId ?: "unnamed"
            Cobblemon.LOGGER.error("[EventTrack] Failed to execute event '$eventName': ${e.message}", e)
        }
    }
}

/**
 * Timed event for the event track.
 */
data class TimedEvent(
    val time: Float,
    val expression: ExpressionLike,
    val eventId: String? = null
)

/**
 * Track that references another track definition by ID.
 *
 * This allows tracks to be defined once in data files and reused across
 * multiple timelines. Track definitions are loaded from
 * `data/<namespace>/camera_tracks/<id>.json`
 *
 * Example usage in timeline:
 * ```json
 * {
 *   "tracks": [
 *     {
 *       "trackType": "reference",
 *       "trackId": "intro_camera_movement"
 *     }
 *   ]
 * }
 * ```
 *
 * The referenced track file (`camera_tracks/intro_camera_movement.json`):
 * ```json
 * {
 *   "trackId": "intro_camera_movement",
 *   "trackType": "motion",
 *   "keyframes": [...]
 * }
 * ```
 *
 * @property trackId ID of the track definition to reference
 *
 * @author Terrainwax
 * @since January 2025
 */
data class TrackReferenceTrack(
    val trackId: String
) : ICameraTrack {
    override val trackType = "reference"

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): Any? {
        // Look up the track from the registry
        val track = CameraTrackDefinitionRegistry.getTrack(trackId)
        if (track == null) {
            Cobblemon.LOGGER.warn("[TrackReferenceTrack] Track '$trackId' not found in registry")
            return null
        }

        // Delegate to the referenced track
        return track.evaluate(time, context, runtime, origin)
    }
}
