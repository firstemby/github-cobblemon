/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.*
import java.lang.reflect.Type

/**
 * JSON adapter for deserializing camera timelines.
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraTimelineAdapter : JsonDeserializer<CameraTimeline> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): CameraTimeline {
        val obj = json.asJsonObject
        val timelineId = obj.get("timelineId")?.asString 
            ?: throw IllegalArgumentException("Timeline missing 'timelineId' field")
        
        val startStateId = obj.get("startStateId")?.asString 
            ?: throw IllegalArgumentException("Timeline '$timelineId' missing 'startStateId' field")
        
        val statesObj = obj.get("states")?.asJsonObject 
            ?: throw IllegalArgumentException("Timeline '$timelineId' missing 'states' object")
        
        val transitionsArray = obj.get("transitions")?.asJsonArray 
            ?: JsonArray()
        
        val states = statesObj.entrySet().associate { (key, value) ->
            try {
                key to deserializeState(value.asJsonObject)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Error parsing state '$key' in timeline '$timelineId': ${e.message}")
                throw IllegalArgumentException("Invalid state in timeline '$timelineId'", e)
            }
        }
        
        val transitions = transitionsArray.mapIndexed { index, element ->
            try {
                deserializeTransition(element.asJsonObject)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Error parsing transition $index in timeline '$timelineId': ${e.message}")
                throw IllegalArgumentException("Invalid transition in timeline '$timelineId'", e)
            }
        }
        
        return CameraTimeline(timelineId, startStateId, states, transitions)
    }
    
    private fun deserializeState(obj: JsonObject): CameraState {
        val stateId = obj.get("stateId")?.asString
            ?: throw IllegalArgumentException("State missing 'stateId' field")

        // Deserialize tracks
        val tracksArray = obj.get("tracks")?.asJsonArray
            ?: throw IllegalArgumentException("State '$stateId' missing 'tracks' field")

        val tracks = tracksArray.mapIndexedNotNull { index, element ->
            try {
                val trackObj = element.asJsonObject
                val trackType = trackObj.get("trackType")?.asString
                    ?: throw IllegalArgumentException("Track $index missing 'trackType' field")

                CameraTrackRegistry.deserializeTrack(trackType, trackObj)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Error parsing track $index in state '$stateId': ${e.message}")
                null // Skip invalid tracks
            }
        }

        // Validate that we have at least one track
        if (tracks.isEmpty()) {
            throw IllegalArgumentException("State '$stateId' must have at least one valid track")
        }

        val isLooping = obj.get("isLooping")?.asBoolean ?: false
        val speed = obj.get("speed")?.asFloat ?: 1.0f

        val interpolation = obj.get("interpolation")?.asJsonObject?.let { interpObj ->
            CameraInterpolation(
                position = interpObj.get("position")?.asString ?: "linear",
                rotation = interpObj.get("rotation")?.asString ?: "spherical"
            )
        } ?: CameraInterpolation()

        val rememberProgress = obj.get("rememberProgress")?.asBoolean ?: false

        // Legacy events (deprecated, use EventTrack instead)
        val events = obj.get("events")?.asJsonArray?.mapIndexed { index, element ->
            try {
                deserializeEvent(element.asJsonObject)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Error parsing event $index in state '$stateId': ${e.message}")
                throw IllegalArgumentException("Invalid event in state '$stateId'", e)
            }
        } ?: emptyList()

        val renderPlayerBody = obj.get("renderPlayerBody")?.asBoolean ?: true

        return CameraState(stateId, tracks, isLooping, speed, interpolation, rememberProgress, renderPlayerBody, events)
    }

    private fun deserializeEvent(obj: JsonObject): CameraEvent {
        val time = obj.get("time")?.asFloat
            ?: throw IllegalArgumentException("Event missing 'time' field")

        val expressionStr = obj.get("expression")?.asString
            ?: throw IllegalArgumentException("Event missing 'expression' field")

        val eventId = obj.get("eventId")?.asString

        return CameraEvent(
            time = time,
            expression = expressionStr.asExpressionLike(),
            eventId = eventId
        )
    }
    
    private fun deserializeTransition(obj: JsonObject): CameraTransition {
        val sourceStateId = obj.get("sourceStateId")?.asString 
            ?: throw IllegalArgumentException("Transition missing 'sourceStateId' field")
        
        val targetStateId = obj.get("targetStateId")?.asString 
            ?: throw IllegalArgumentException("Transition missing 'targetStateId' field")
        
        val conditionStr = obj.get("condition")?.asString 
            ?: throw IllegalArgumentException("Transition from '$sourceStateId' to '$targetStateId' missing 'condition' field")
        
        val blendDuration = obj.get("blendDuration")?.asFloat ?: 0.4f
        
        val transitionType = obj.get("transitionType")?.asString?.let { typeStr ->
            when (typeStr.lowercase()) {
                "blend" -> TransitionType.BLEND
                "instant" -> TransitionType.INSTANT
                else -> {
                    Cobblemon.LOGGER.warn("[CobblemonCamera] Unknown transition type '$typeStr', using BLEND")
                    TransitionType.BLEND
                }
            }
        } ?: TransitionType.BLEND
        
        return CameraTransition(
            sourceStateId = sourceStateId,
            targetStateId = targetStateId,
            condition = conditionStr.asExpressionLike(),
            blendDuration = blendDuration,
            transitionType = transitionType
        )
    }
}

