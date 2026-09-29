/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.registry

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiAction
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiActionType
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiArgValue
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiTrack
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.StartPose
import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

object SceneDeserializer : JsonDeserializer<Scene> {

    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): Scene {
        val obj = json.asJsonObject

        val version = obj.get("version")
        if (version?.isJsonPrimitive != true || !version.asJsonPrimitive.isNumber ||
            version.asString.toBigDecimalOrNull()?.compareTo(2.toBigDecimal()) != 0) {
            throw JsonParseException("Unsupported cinematic scene version $version; expected 2")
        }

        val id = obj.get("sceneId")?.asString
            ?: throw JsonParseException("Scene missing 'sceneId' field")

        val startBeatId = obj.get("startBeatId")?.asString
            ?: throw JsonParseException("Scene '$id' missing 'startBeatId' field")

        val parameters = parseParameters(obj.get("parameters")?.asJsonObject, id)
        val actors = parseActors(obj.get("actors")?.asJsonObject, id)
        val shots = parseShots(obj.get("shots")?.asJsonObject, id)
        val feeds = parseFeeds(obj.get("feeds")?.asJsonArray)
        val beats = parseBeats(obj.get("beats")?.asJsonObject, id)
        val transitions = parseTransitions(obj.get("transitions")?.asJsonArray, id)
        val damageImmunity = obj.get("damageImmunityDuringTakeover")?.asBoolean ?: false
        val disconnectPolicy = obj.get("disconnectPolicy")?.asString
            ?.let { parseEnum<DisconnectPolicy>(it, "Scene '$id' disconnectPolicy") }
            ?: DisconnectPolicy.END_SCENE

        return Scene(
            id = id,
            parameters = parameters,
            actors = actors,
            shots = shots,
            feeds = feeds,
            startBeatId = startBeatId,
            beats = beats,
            transitions = transitions,
            version = 2,
            damageImmunityDuringTakeover = damageImmunity,
            disconnectPolicy = disconnectPolicy
        )
    }

    private fun parseParameters(obj: JsonObject?, sceneId: String): Map<String, ParameterDefinition> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (name, value) ->
            name to parseParameter(value.asJsonObject, name, sceneId)
        }
    }

    private fun parseParameter(obj: JsonObject, paramName: String, sceneId: String): ParameterDefinition {
        val typeStr = obj.get("type")?.asString
            ?: throw JsonParseException("Parameter '$paramName' in scene '$sceneId' missing 'type'")
        val type = when (typeStr.lowercase()) {
            "number" -> ParameterType.NUMBER
            "vec3" -> ParameterType.VEC3
            "string" -> ParameterType.STRING
            else -> throw JsonParseException("Parameter '$paramName' in scene '$sceneId' has unknown type '$typeStr'")
        }
        val description = obj.get("description")?.asString
        val default: ParameterDefault? = when {
            !obj.has("default") -> null
            type == ParameterType.NUMBER -> {
                val number = obj.get("default").asDouble
                if (!number.isFinite()) throw JsonParseException("Parameter '$paramName' in scene '$sceneId' requires a finite default")
                ParameterDefault.NumberDefault(number)
            }
            type == ParameterType.VEC3 -> {
                val vec = obj.get("default").asJsonObject
                val x = vec.get("x").asDouble
                val y = vec.get("y").asDouble
                val z = vec.get("z").asDouble
                if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
                    throw JsonParseException("Parameter '$paramName' in scene '$sceneId' requires a finite default vector")
                }
                ParameterDefault.Vec3Default(x, y, z)
            }
            type == ParameterType.STRING -> ParameterDefault.StringDefault(obj.get("default").asString)
            else -> null
        }
        return ParameterDefinition(type = type, description = description, default = default)
    }

    private fun parseActors(obj: JsonObject?, sceneId: String): Map<String, ActorDeclaration> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (key, value) ->
            val actorObj = value.asJsonObject
            val id = actorObj.get("id")?.asString ?: key
            val kindStr = actorObj.get("kind")?.asString
                ?: throw JsonParseException("Actor '$key' in scene '$sceneId' missing 'kind'")
            val kind = when (kindStr.lowercase()) {
                "player_takeover" -> ActorKind.PlayerTakeover
                "real_entity" -> ActorKind.RealEntity
                "synthesized_pokemon" -> ActorKind.SynthesizedPokemon
                "synthesized_bedrock" -> ActorKind.SynthesizedBedrock
                else -> throw JsonParseException("Actor '$key' in scene '$sceneId' has unknown kind '$kindStr'")
            }
            key to ActorDeclaration(id = id, kind = kind)
        }
    }

    private fun parseShots(obj: JsonObject?, sceneId: String): Map<String, Shot> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (key, value) ->
            val shotObj = value.asJsonObject
            val id = shotObj.get("id")?.asString ?: key

            val positionObj = shotObj.get("position")?.asJsonObject
                ?: throw JsonParseException("Shot '$key' in scene '$sceneId' missing 'position'")
            val rotationObj = shotObj.get("rotation")?.asJsonObject
                ?: throw JsonParseException("Shot '$key' in scene '$sceneId' missing 'rotation'")

            val positionType = positionObj.get("type")?.asString
                ?: throw JsonParseException("Shot '$key' position missing 'type' discriminator")
            val position = SceneSourceRegistry.deserializePositionSource(positionType, positionObj)
                ?: throw JsonParseException("Shot '$key' position type '$positionType' not registered")

            val rotationType = rotationObj.get("type")?.asString
                ?: throw JsonParseException("Shot '$key' rotation missing 'type' discriminator")
            val rotation = SceneSourceRegistry.deserializeRotationSource(rotationType, rotationObj)
                ?: throw JsonParseException("Shot '$key' rotation type '$rotationType' not registered")

            val fov = shotObj.get("fov")?.asJsonObject?.let { fovObj ->
                val tag = fovObj.get("type")?.asString
                    ?: throw JsonParseException("Shot '$key' fov missing 'type' discriminator")
                SceneSourceRegistry.deserializeFovSource(tag, fovObj)
                    ?: throw JsonParseException("Shot '$key' fov type '$tag' not registered")
            }

            val roll = shotObj.get("roll")?.asJsonObject?.let { rollObj ->
                val tag = rollObj.get("type")?.asString
                    ?: throw JsonParseException("Shot '$key' roll missing 'type' discriminator")
                SceneSourceRegistry.deserializeRollSource(tag, rollObj)
                    ?: throw JsonParseException("Shot '$key' roll type '$tag' not registered")
            }

            val lens = shotObj.get("lens")?.asJsonObject?.let { parseLens(it) }

            val modifiers = shotObj.get("modifiers")?.asJsonArray?.map { element ->
                val modObj = element.asJsonObject
                val tag = modObj.get("type")?.asString
                    ?: throw JsonParseException("Shot '$key' modifier missing 'type' discriminator")
                SceneSourceRegistry.deserializeModifier(tag, modObj)
                    ?: throw JsonParseException("Shot '$key' modifier type '$tag' not registered")
            } ?: emptyList()

            key to Shot(
                id = id,
                position = position,
                rotation = rotation,
                fov = fov,
                roll = roll,
                lens = lens,
                modifiers = modifiers
            )
        }
    }

    private fun parseLens(obj: JsonObject): Lens {
        val aspectCrop = if (obj.has("aspectCrop")) obj.get("aspectCrop").asFloat else null
        val letterbox = obj.get("letterbox")?.asFloat ?: 0f
        return Lens(aspectCrop = aspectCrop, letterbox = letterbox)
    }

    private fun parseFeeds(array: JsonArray?): List<FeedDeclaration> {
        if (array == null) return emptyList()
        return array.map { element ->
            val obj = element.asJsonObject
            val id = obj.get("id")?.asString
                ?: throw JsonParseException("Feed missing 'id'")
            val kindObj = obj.get("kind")?.asJsonObject
                ?: throw JsonParseException("Feed '$id' missing 'kind'")
            val kindType = kindObj.get("type")?.asString
                ?: throw JsonParseException("Feed '$id' kind missing 'type' discriminator")
            val kind = when (kindType.lowercase()) {
                "fullscreen" -> FeedKind.Fullscreen
                "pip" -> FeedKind.Pip(
                    corner = FeedKind.Corner.valueOf(kindObj.get("corner").asString.uppercase()),
                    size = kindObj.get("size").asFloat
                )
                "split" -> FeedKind.Split(
                    side = FeedKind.Side.valueOf(kindObj.get("side").asString.uppercase()),
                    size = kindObj.get("size").asFloat
                )
                "diagonal" -> FeedKind.Diagonal(
                    half = FeedKind.DiagonalHalf.valueOf(kindObj.get("half").asString.uppercase())
                )
                else -> throw JsonParseException("Feed '$id' unknown kind type '$kindType'")
            }
            FeedDeclaration(id = id, kind = kind)
        }
    }

    private fun parseBeats(obj: JsonObject?, sceneId: String): Map<String, Beat> {
        if (obj == null) throw JsonParseException("Scene '$sceneId' missing 'beats'")
        return obj.entrySet().associate { (key, value) ->
            val beatObj = value.asJsonObject
            val id = beatObj.get("id")?.asString ?: key
            val duration = if (beatObj.has("duration")) beatObj.get("duration").asFloat else null
            val isLooping = beatObj.get("isLooping")?.asBoolean ?: false
            val speed = beatObj.get("speed")?.asFloat ?: 1.0f
            val actorDirections = parseActorDirections(beatObj.get("actorDirections")?.asJsonObject, id, sceneId)
            val cuts = parseCuts(beatObj.get("cuts")?.asJsonObject, id)
            val renderPlayerBody = beatObj.get("renderPlayerBody")?.asBoolean ?: true
            val hiddenHudElements = parseHudSet(beatObj.get("hiddenHudElements"))
            val guiTrack: SceneGuiTrack? = parseGuiTrack(beatObj.get("guiTrack")?.asJsonObject)

            key to Beat(
                id = id,
                duration = duration,
                isLooping = isLooping,
                speed = speed,
                actorDirections = actorDirections,
                cuts = cuts,
                guiTrack = guiTrack,
                renderPlayerBody = renderPlayerBody,
                hiddenHudElements = hiddenHudElements,
                cues = beatObj.getAsJsonArray("cues")?.map { element ->
                    val cue = element.asJsonObject
                    SceneCue(cue.get("id").asString, cue.get("time").asFloat)
                }.orEmpty()
            )
        }
    }

    private fun parseGuiTrack(obj: JsonObject?): SceneGuiTrack? {
        if (obj == null) return null
        val actionsArray = obj.getAsJsonArray("actions") ?: throw JsonParseException("guiTrack missing 'actions'")
        val actions = actionsArray.map { element ->
            val a = element.asJsonObject
            val time = a.get("time")?.asFloat
                ?: throw JsonParseException("guiTrack action missing 'time'")
            val actionType = a.get("action")?.asString
                ?: throw JsonParseException("guiTrack action missing 'action'")
            val guiId = a.get("guiId")?.asString
                ?: throw JsonParseException("guiTrack action missing 'guiId'")
            val args = mutableMapOf<String, SceneGuiArgValue>()
            a.getAsJsonObject("args")?.entrySet()?.forEach { (key, value) ->
                val entry = value.asJsonObject
                val argType = entry.get("type")?.asString
                    ?: throw JsonParseException("guiTrack action '$guiId' arg '$key' missing 'type' (expected 'expression' or 'string')")
                val argVal = entry.get("value")?.asString
                    ?: throw JsonParseException("guiTrack action '$guiId' arg '$key' missing 'value'")
                args[key] = when (argType.lowercase()) {
                    "expression" -> SceneGuiArgValue.Expression(argVal)
                    "string" -> SceneGuiArgValue.StringLiteral(argVal)
                    else -> throw JsonParseException("guiTrack action '$guiId' arg '$key' unknown type '$argType'")
                }
            }
            SceneGuiAction(
                time = time,
                type = parseEnum<SceneGuiActionType>(actionType, "guiTrack '$guiId' action"),
                guiId = guiId,
                args = args
            )
        }
        return SceneGuiTrack(actions)
    }

    private fun parseActorDirections(obj: JsonObject?, beatId: String, sceneId: String): Map<String, ActorDirection> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (key, value) ->
            val dirObj = value.asJsonObject
            val visible = if (dirObj.has("visible")) dirObj.get("visible").asBoolean else null
            val startPose = dirObj.get("startPose")?.asJsonObject?.let { parseStartPose(it) }
            val startPoseMode = dirObj.get("startPoseMode")?.asString?.let { s ->
                parseEnum<StartPoseMode>(s, "Actor '$key' in scene '$sceneId' beat '$beatId' startPoseMode")
            }
            val tracks = dirObj.get("tracks")?.asJsonArray?.map { element ->
                val trackObj = element.asJsonObject
                val tag = trackObj.get("trackType")?.asString
                    ?: throw JsonParseException("Actor '$key' in scene '$sceneId' beat '$beatId' track missing 'trackType' discriminator")
                SceneSourceRegistry.deserializeActorTrack(tag, trackObj)
                    ?: throw JsonParseException("Actor '$key' track type '$tag' not registered in scene '$sceneId' beat '$beatId'")
            } ?: emptyList()
            key to ActorDirection(
                visible = visible,
                startPose = startPose,
                startPoseMode = startPoseMode,
                tracks = tracks
            )
        }
    }

    private fun parseStartPose(obj: JsonObject): StartPose {
        val positionObj = obj.get("position")
            ?: throw JsonParseException("StartPose missing 'position'")
        val position = PositionExpression.fromJson(positionObj)
        val rotObj = obj.get("rotation")?.asJsonObject
            ?: throw JsonParseException("StartPose missing 'rotation'")
        val rotation = CameraRotation(
            yaw = rotObj.get("yaw").asFloat,
            pitch = rotObj.get("pitch").asFloat
        )
        val blendDurationSeconds = obj.get("blendDurationSeconds")?.asFloat ?: 0.5f
        return StartPose(position = position, rotation = rotation, blendDurationSeconds = blendDurationSeconds)
    }

    private fun parseCuts(obj: JsonObject?, beatId: String): Map<String, CutTrack> {
        if (obj == null) return emptyMap()
        return obj.entrySet().associate { (feedId, cutValue) ->
            val entriesArray = cutValue.asJsonObject.get("entries")?.asJsonArray
                ?: throw JsonParseException("Beat '$beatId' cut for feed '$feedId' missing 'entries'")
            val entries = entriesArray.map { element ->
                val entryObj = element.asJsonObject
                CutEntry(
                    time = entryObj.get("time").asFloat,
                    shotId = entryObj.get("shotId").asString,
                    transition = parseCutTransition(entryObj.get("transition"))
                )
            }
            feedId to CutTrack(entries = entries)
        }
    }

    private fun parseCutTransition(element: JsonElement?): CutTransition {
        if (element == null) return CutTransition.Instant
        val obj = element.asJsonObject
        val type = obj.get("type")?.asString ?: "instant"
        return when (type.lowercase()) {
            "instant" -> CutTransition.Instant
            "blend" -> CutTransition.Blend(duration = obj.get("duration").asFloat)
            else -> throw JsonParseException("Unknown CutTransition type '$type'")
        }
    }

    private fun parseHudSet(element: JsonElement?): Set<HudElement> {
        if (element == null) return emptySet()
        if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            return if (element.asBoolean) HudElement.ALL else emptySet()
        }
        if (element.isJsonArray) {
            return element.asJsonArray.map {
                val id = it.asString
                HudElement.fromId(id) ?: throw JsonParseException("Unknown hidden HUD element '$id'")
            }.toSet()
        }
        throw JsonParseException("hiddenHudElements must be a boolean or an array of HUD element IDs")
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, field: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw JsonParseException("$field has unknown value '$value'")

    private fun parseTransitions(array: JsonArray?, sceneId: String): List<BeatTransition> {
        if (array == null) return emptyList()
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObject
            val sourceBeatId = obj.get("sourceBeatId")?.asString
                ?: throw JsonParseException("Scene '$sceneId' transition $index missing 'sourceBeatId'")
            val endsScene = obj.get("endsScene")?.asBoolean ?: false
            val targetBeatId = obj.get("targetBeatId")?.asString
                ?: if (endsScene) "" else throw JsonParseException("Scene '$sceneId' transition $index missing 'targetBeatId'")
            val condition = (obj.get("condition")?.asString ?: "1.0").asExpressionLike()
            val transition = parseCutTransition(obj.get("transition"))
            BeatTransition(
                sourceBeatId = sourceBeatId,
                targetBeatId = targetBeatId,
                condition = condition,
                transition = transition,
                endsScene = endsScene
            )
        }
    }
}
