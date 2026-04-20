/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.registry

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.cobblemon.mod.common.api.camera.model.*
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.*
import java.lang.reflect.Type

/**
 * JSON deserializer for Timeline (V2 format).
 *
 * Deserializes directly into the immutable Timeline model without legacy conversion.
 */
object TimelineDeserializer : JsonDeserializer<Timeline> {

    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): Timeline {
        val obj = json.asJsonObject

        val id = obj.get("timelineId")?.asString
            ?: throw JsonParseException("Timeline missing 'timelineId' field")

        val startStateId = obj.get("startStateId")?.asString
            ?: throw JsonParseException("Timeline '$id' missing 'startStateId' field")

        val statesObj = obj.get("states")?.asJsonObject
            ?: throw JsonParseException("Timeline '$id' missing 'states' object")

        val transitionsArray = obj.get("transitions")?.asJsonArray ?: JsonArray()

        // Parse parameters (optional)
        val parametersObj = obj.get("parameters")?.asJsonObject
        val parameters = parametersObj?.entrySet()?.associate { (paramName, paramValue) ->
            try {
                paramName to deserializeParameter(paramValue.asJsonObject, paramName, id)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[Timeline] Error parsing parameter '$paramName' in timeline '$id': ${e.message}")
                throw JsonParseException("Invalid parameter '$paramName' in timeline '$id'", e)
            }
        } ?: emptyMap()

        val states = statesObj.entrySet().associate { (key, value) ->
            try {
                key to deserializeState(value.asJsonObject, id)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[Timeline] Error parsing state '$key' in timeline '$id': ${e.message}")
                throw JsonParseException("Invalid state '$key' in timeline '$id'", e)
            }
        }

        val transitions = transitionsArray.mapIndexed { index, element ->
            try {
                deserializeTransition(element.asJsonObject, id)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[Timeline] Error parsing transition $index in timeline '$id': ${e.message}")
                throw JsonParseException("Invalid transition $index in timeline '$id'", e)
            }
        }

        return Timeline(
            id = id,
            startStateId = startStateId,
            parameters = parameters,
            states = states,
            transitions = transitions
        )
    }

    private fun deserializeParameter(obj: JsonObject, paramName: String, timelineId: String): ParameterDefinition {
        val typeStr = obj.get("type")?.asString
            ?: throw JsonParseException("Parameter '$paramName' missing 'type' field")

        val type = when (typeStr.lowercase()) {
            "number" -> ParameterType.NUMBER
            "vec3" -> ParameterType.VEC3
            else -> throw JsonParseException("Parameter '$paramName' has unknown type '$typeStr'. Valid types: number, vec3")
        }

        val description = obj.get("description")?.asString

        val default: ParameterDefault? = when {
            !obj.has("default") -> null
            type == ParameterType.NUMBER -> {
                val defaultValue = obj.get("default")
                if (defaultValue.isJsonPrimitive && defaultValue.asJsonPrimitive.isNumber) {
                    ParameterDefault.NumberDefault(defaultValue.asDouble)
                } else {
                    throw JsonParseException("Parameter '$paramName' default must be a number")
                }
            }
            type == ParameterType.VEC3 -> {
                val defaultObj = obj.get("default")
                if (defaultObj.isJsonObject) {
                    val vec = defaultObj.asJsonObject
                    val x = vec.get("x")?.asDouble
                        ?: throw JsonParseException("Parameter '$paramName' vec3 default missing 'x' field")
                    val y = vec.get("y")?.asDouble
                        ?: throw JsonParseException("Parameter '$paramName' vec3 default missing 'y' field")
                    val z = vec.get("z")?.asDouble
                        ?: throw JsonParseException("Parameter '$paramName' vec3 default missing 'z' field")
                    ParameterDefault.Vec3Default(x, y, z)
                } else {
                    throw JsonParseException("Parameter '$paramName' vec3 default must be an object with x, y, z fields")
                }
            }
            else -> null
        }

        return ParameterDefinition(
            type = type,
            description = description,
            default = default
        )
    }

    private fun deserializeState(obj: JsonObject, timelineId: String): State {
        val stateId = obj.get("stateId")?.asString
            ?: throw JsonParseException("State missing 'stateId' field")

        val tracksArray = obj.get("tracks")?.asJsonArray
            ?: throw JsonParseException("State '$stateId' missing 'tracks' array")

        val tracks = tracksArray.mapIndexedNotNull { index, element ->
            try {
                deserializeTrack(element.asJsonObject)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[Timeline] Error parsing track $index in state '$stateId': ${e.message}", e)
                null
            }
        }

        val duration = obj.get("duration")?.asFloat
        val isLooping = obj.get("isLooping")?.asBoolean ?: false
        val speed = obj.get("speed")?.asFloat ?: 1.0f
        val rememberProgress = obj.get("rememberProgress")?.asBoolean ?: false
        val renderPlayerBody = obj.get("renderPlayerBody")?.asBoolean ?: true

        val hiddenHudElements = when (val hideHudElement = obj.get("hideHud")) {
            null -> emptySet()
            is JsonPrimitive -> {
                if (hideHudElement.isBoolean) {
                    if (hideHudElement.asBoolean) HudElement.ALL else emptySet()
                } else {
                    Cobblemon.LOGGER.warn("[Timeline] Invalid hideHud value in state '$stateId', expected boolean or array")
                    emptySet()
                }
            }
            is JsonArray -> {
                hideHudElement.mapNotNull { element ->
                    val id = element.asString
                    HudElement.fromId(id) ?: run {
                        Cobblemon.LOGGER.warn("[Timeline] Unknown HUD element '$id' in state '$stateId'")
                        null
                    }
                }.toSet()
            }
            else -> {
                Cobblemon.LOGGER.warn("[Timeline] Invalid hideHud value in state '$stateId', expected boolean or array")
                emptySet()
            }
        }

        return State(
            id = stateId,
            tracks = tracks,
            duration = duration,
            isLooping = isLooping,
            speed = speed,
            rememberProgress = rememberProgress,
            renderPlayerBody = renderPlayerBody,
            hiddenHudElements = hiddenHudElements
        )
    }

    private fun deserializeTrack(obj: JsonObject): CameraTrack {
        val trackType = obj.get("trackType")?.asString
            ?: throw JsonParseException("Track missing 'trackType' field")

        return CameraTrackRegistry.deserializeTrack(trackType, obj)
            ?: throw JsonParseException("Unknown track type: $trackType")
    }

    private fun deserializeTransition(obj: JsonObject, timelineId: String): Transition {
        val sourceStateId = obj.get("sourceStateId")?.asString
            ?: throw JsonParseException("Transition missing 'sourceStateId' field")

        val targetStateId = obj.get("targetStateId")?.asString
            ?: throw JsonParseException("Transition missing 'targetStateId' field")

        val conditionStr = obj.get("condition")?.asString
            ?: throw JsonParseException("Transition from '$sourceStateId' to '$targetStateId' missing 'condition' field")

        val blendDuration = obj.get("blendDuration")?.asFloat ?: 0.4f

        val transitionType = obj.get("transitionType")?.asString?.let { typeStr ->
            when (typeStr.lowercase()) {
                "blend" -> TransitionType.BLEND
                "instant" -> TransitionType.INSTANT
                else -> {
                    Cobblemon.LOGGER.warn("[Timeline] Unknown transition type '$typeStr' in timeline '$timelineId', using BLEND")
                    TransitionType.BLEND
                }
            }
        } ?: TransitionType.BLEND

        return Transition(
            sourceStateId = sourceStateId,
            targetStateId = targetStateId,
            condition = conditionStr.asExpressionLike(),
            type = transitionType,
            blendDuration = blendDuration
        )
    }
}
