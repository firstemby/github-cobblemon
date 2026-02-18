/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.track

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.cobblemon.mod.common.api.camera.interpolation.InterpolationMethod
import com.cobblemon.mod.common.api.camera.model.CameraFocus
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonObject

/**
 * Handles registration of built-in track type deserializers.
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
        CameraTrackRegistry.registerTrackType("gui", ::deserializeGuiTrack)

        Cobblemon.LOGGER.debug("[CameraTrackDeserializers] Registered built-in track types: position, focus, event, reference, gui")
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
    private fun deserializePositionTrack(json: JsonObject): PositionTrack {
        val keyframesArray = json.getAsJsonArray("keyframes")
            ?: throw IllegalArgumentException("Position track missing 'keyframes' array")

        val keyframes = keyframesArray.map { element ->
            val kfObj = element.asJsonObject
            val time = kfObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("Position keyframe missing 'time' field")

            val positionElement = kfObj.get("position")
                ?: throw IllegalArgumentException("Position keyframe missing 'position' field")

            val position = PositionExpression.fromJson(positionElement)
            val easing = kfObj.get("easing")?.asString

            PositionKeyframe(time, position, easing)
        }

        val interpolationTypeStr = json.get("interpolationType")?.asString ?: "linear"
        val interpolationType = when (interpolationTypeStr.lowercase()) {
            "linear" -> InterpolationMethod.LINEAR
            "bezier", "cubic_bezier" -> InterpolationMethod.BEZIER
            "hermite", "catmull_rom" -> InterpolationMethod.BEZIER // Map legacy types to available ones
            else -> {
                Cobblemon.LOGGER.warn("[CameraTrackDeserializers] Unknown interpolation type '$interpolationTypeStr', using LINEAR")
                InterpolationMethod.LINEAR
            }
        }

        val defaultEasing = json.get("easing")?.asString ?: "linear"
        val isRelative = json.get("isRelative")?.asBoolean ?: false

        return PositionTrack(keyframes, interpolationType, defaultEasing, isRelative)
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
    private fun deserializeFocusTrack(json: JsonObject): FocusTrack {
        val keyframesArray = json.getAsJsonArray("keyframes")
            ?: throw IllegalArgumentException("Focus track missing 'keyframes' array")

        val keyframes = keyframesArray.map { element ->
            val kfObj = element.asJsonObject
            val time = kfObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("Focus keyframe missing 'time' field")

            val targetObj = kfObj.getAsJsonObject("target")
                ?: throw IllegalArgumentException("Focus keyframe missing 'target' field")

            val target = CameraFocus.fromJson(targetObj)
            val easing = kfObj.get("easing")?.asString

            FocusKeyframe(time, target, easing)
        }

        val defaultEasing = json.get("easing")?.asString ?: "linear"

        return FocusTrack(keyframes, defaultEasing)
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
    private fun deserializeEventTrack(json: JsonObject): EventTrack {
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
    private fun deserializeReferenceTrack(json: JsonObject): ReferenceTrack {
        val trackId = json.get("trackId")?.asString
            ?: throw IllegalArgumentException("Reference track missing 'trackId' field")

        return ReferenceTrack(trackId)
    }

    /**
     * Deserializes a GUI track from JSON.
     *
     * Expected JSON format:
     * ```json
     * {
     *   "trackType": "gui",
     *   "actions": [
     *     {
     *       "time": 0.0,
     *       "action": "OPEN",
     *       "guiId": "cobblemon:battle_intro",
     *       "args": { "title": "Stadium Battle" },
     *       "argsExpressions": {
     *         "player_party_count": "variable.player_party_count"
     *       }
     *     },
     *     {
     *       "time": 5.0,
     *       "action": "CLOSE",
     *       "guiId": "cobblemon:battle_intro"
     *     }
     *   ]
     * }
     * ```
     */
    private fun deserializeGuiTrack(json: JsonObject): GuiTrack {
        val actionsArray = json.getAsJsonArray("actions")
            ?: throw IllegalArgumentException("GUI track missing 'actions' array")

        val actions = actionsArray.map { element ->
            val actionObj = element.asJsonObject
            val time = actionObj.get("time")?.asFloat
                ?: throw IllegalArgumentException("GUI action missing 'time' field")
            val actionType = actionObj.get("action")?.asString
                ?: throw IllegalArgumentException("GUI action missing 'action' field")
            val guiId = actionObj.get("guiId")?.asString
                ?: throw IllegalArgumentException("GUI action missing 'guiId' field")

            val args = mutableMapOf<String, String>()
            actionObj.getAsJsonObject("args")?.entrySet()?.forEach { (key, value) ->
                args[key] = value.asString
            }

            val argsExpressions = mutableMapOf<String, String>()
            actionObj.getAsJsonObject("argsExpressions")?.entrySet()?.forEach { (key, value) ->
                argsExpressions[key] = value.asString
            }

            TimedGuiAction(
                time = time,
                action = GuiActionType.valueOf(actionType.uppercase()),
                guiId = guiId,
                args = args,
                argsExpressions = argsExpressions
            )
        }

        return GuiTrack(actions)
    }
}
