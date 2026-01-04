/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera.tracks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.client.camera.LookTarget
import com.cobblemon.mod.common.client.camera.PathInterpolationType
import com.cobblemon.mod.common.client.camera.PositionExpression
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonObject

/**
 * Handles registration of built-in track type deserializers.
 *
 * This object registers the three core track types (position, focus, event)
 * with the CameraTrackRegistry. Third-party mods can register additional
 * track types using the same registry.
 *
 * @author Terrainwax
 * @since January 2025
 */
object CameraTrackDeserializers {

    /**
     * Registers all built-in track type deserializers.
     * Should be called during mod initialization.
     */
    fun registerBuiltinTracks() {
        CameraTrackRegistry.registerTrackType("position", ::deserializePositionTrack)
        CameraTrackRegistry.registerTrackType("focus", ::deserializeFocusTrack)
        CameraTrackRegistry.registerTrackType("event", ::deserializeEventTrack)
        CameraTrackRegistry.registerTrackType("reference", ::deserializeReferenceTrack)

        Cobblemon.LOGGER.debug("[CameraTrackDeserializers] Registered built-in track types: position, focus, event, reference")
    }

    /**
     * Deserializes a position track from JSON.
     *
     * Expected JSON format:
     * ```json
     * {
     *   "trackType": "position",
     *   "keyframes": [
     *     {
     *       "time": 0.0,
     *       "position": { "x": 10.0, "y": 64.0, "z": 10.0 },
     *       "easing": "ease_in_out_quad"
     *     }
     *   ],
     *   "interpolationType": "bezier",
     *   "easing": "linear",
     *   "isRelative": false
     * }
     * ```
     */
    private fun deserializePositionTrack(json: JsonObject): ICameraTrack {
        val keyframesArray = json.getAsJsonArray("keyframes")
            ?: throw IllegalArgumentException("Position track missing 'keyframes' array")

        val keyframes = keyframesArray.map { element ->
            val kfObj = element.asJsonObject
            val time = kfObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("Position keyframe missing 'time' field")

            val positionObj = kfObj.getAsJsonObject("position")
                ?: throw IllegalArgumentException("Position keyframe missing 'position' field")

            val position = PositionExpression.fromJson(positionObj)
            val easing = kfObj.get("easing")?.asString

            PositionKeyframe(time, position, easing)
        }

        val interpolationTypeStr = json.get("interpolationType")?.asString ?: "linear"
        val interpolationType = PathInterpolationType.valueOf(interpolationTypeStr.uppercase())

        val easing = json.get("easing")?.asString ?: "linear"
        val isRelative = json.get("isRelative")?.asBoolean ?: false

        return PositionTrack(keyframes, interpolationType, easing, isRelative)
    }

    /**
     * Deserializes a focus track from JSON.
     *
     * Expected JSON format:
     * ```json
     * {
     *   "trackType": "focus",
     *   "keyframes": [
     *     {
     *       "time": 0.0,
     *       "target": { "type": "position", "x": 20.0, "y": 60.0, "z": 20.0 },
     *       "easing": "linear"
     *     }
     *   ],
     *   "easing": "linear"
     * }
     * ```
     */
    private fun deserializeFocusTrack(json: JsonObject): ICameraTrack {
        val keyframesArray = json.getAsJsonArray("keyframes")
            ?: throw IllegalArgumentException("Focus track missing 'keyframes' array")

        val keyframes = keyframesArray.map { element ->
            val kfObj = element.asJsonObject
            val time = kfObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("Focus keyframe missing 'time' field")

            val targetObj = kfObj.getAsJsonObject("target")
                ?: throw IllegalArgumentException("Focus keyframe missing 'target' field")

            val target = LookTarget.fromJson(targetObj)
            val easing = kfObj.get("easing")?.asString

            FocusKeyframe(time, target, easing)
        }

        val easing = json.get("easing")?.asString ?: "linear"

        return FocusTrack(keyframes, easing)
    }

    /**
     * Deserializes an event track from JSON.
     *
     * Expected JSON format:
     * ```json
     * {
     *   "trackType": "event",
     *   "events": [
     *     {
     *       "time": 0.5,
     *       "expression": "q.play_sound('cobblemon:poke_ball.open')",
     *       "eventId": "ball_open"
     *     }
     *   ]
     * }
     * ```
     */
    private fun deserializeEventTrack(json: JsonObject): ICameraTrack {
        val eventsArray = json.getAsJsonArray("events")
            ?: throw IllegalArgumentException("Event track missing 'events' array")

        val events = eventsArray.map { element ->
            val eventObj = element.asJsonObject
            val time = eventObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("Event missing 'time' field")

            val expressionStr = eventObj.get("expression")?.asString
                ?: throw IllegalArgumentException("Event missing 'expression' field")

            val eventId = eventObj.get("eventId")?.asString

            TimedEvent(time, expressionStr.asExpressionLike(), eventId)
        }

        return EventTrack(events)
    }

    /**
     * Deserializes a reference track from JSON.
     *
     * Expected JSON format:
     * ```json
     * {
     *   "trackType": "reference",
     *   "trackId": "intro_camera_movement"
     * }
     * ```
     */
    private fun deserializeReferenceTrack(json: JsonObject): ICameraTrack {
        val trackId = json.get("trackId")?.asString
            ?: throw IllegalArgumentException("Reference track missing 'trackId' field")

        return TrackReferenceTrack(trackId)
    }
}
