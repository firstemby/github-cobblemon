/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import net.minecraft.world.phys.Vec3
import kotlin.io.path.readText
import kotlin.io.path.writeText

object SceneSerializer {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Scene::class.java, SceneDeserializer)
        .create()

    fun loadFromFile(path: Path): EditorScene? {
        val json = JsonParser.parseString(path.readText())
        val root = json.asJsonObject
        val version = root.get("version")?.asInt ?: 1
        if (version != 2) return null
        val scene = gson.fromJson(json, Scene::class.java)
        val editor = fromScene(scene, path, root)
        return editor
    }

    fun saveToFile(editor: EditorScene, path: Path) {
        val json = gson.toJson(toJson(editor))
        val target = path.toAbsolutePath().normalize()
        val temporary = Files.createTempFile(target.parent, ".cinematic-", ".tmp")
        try {
            temporary.writeText(json)
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        editor.sourcePath = path
        editor.clearDirty()
    }

    fun fromScene(scene: Scene, sourcePath: Path? = null, sourceJson: JsonObject): EditorScene {
        val editor = EditorScene(
            id = scene.id,
            startBeatId = scene.startBeatId,
            sourcePath = sourcePath,
            damageImmunityDuringTakeover = scene.damageImmunityDuringTakeover,
            disconnectPolicy = scene.disconnectPolicy
        )
        scene.parameters.forEach { (name, def) -> editor.parameters[name] = def }
        scene.actors.forEach { (id, actor) ->
            val preview = sourceJson.getAsJsonObject("actors")?.getAsJsonObject(id)?.getAsJsonObject("preview")
            editor.actors[id] = EditorActor(id = actor.id, kind = actor.kind,
                previewSpecies = preview?.get("species")?.asString.orEmpty(),
                previewCategory = preview?.get("category")?.asString.orEmpty(),
                previewScale = preview?.get("scale")?.asFloat ?: 1f,
                previewOffset = preview?.getAsJsonObject("offset")?.let { Vec3(it.get("x").asDouble, it.get("y").asDouble, it.get("z").asDouble) } ?: Vec3.ZERO,
                previewAspects = preview?.getAsJsonArray("aspects")?.map { it.asString }?.toMutableSet() ?: mutableSetOf())
        }
        scene.feeds.forEach { feed ->
            editor.feeds.add(EditorFeed(id = feed.id, kind = feed.kind))
        }
        val shotsJson = sourceJson.getAsJsonObject("shots")
        shotsJson?.entrySet()?.forEach { (shotId, shotElement) ->
            val shotObj = shotElement.asJsonObject.deepCopy()
            editor.shots[shotId] = EditorShot(
                id = shotId,
                positionData = shotObj.getAsJsonObject("position"),
                rotationData = shotObj.getAsJsonObject("rotation"),
                fovData = shotObj.get("fov")?.asJsonObject,
                rollData = shotObj.get("roll")?.asJsonObject,
                lens = shotObj.get("lens")?.asJsonObject?.let { lensObj ->
                    EditorLens(
                        aspectCrop = if (lensObj.has("aspectCrop")) lensObj.get("aspectCrop").asFloat else null,
                        letterbox = lensObj.get("letterbox")?.asFloat ?: 0f
                    )
                },
                modifiers = shotObj.get("modifiers")?.asJsonArray?.map { it.asJsonObject }?.toMutableList()
                    ?: mutableListOf()
            )
        }
        scene.beats.forEach { (beatId, beat) ->
            editor.beats[beatId] = EditorBeat(
                id = beat.id,
                duration = beat.duration,
                isLooping = beat.isLooping,
                speed = beat.speed,
                renderPlayerBody = beat.renderPlayerBody,
                hiddenHudElements = beat.hiddenHudElements.toMutableSet(),
                cues = beat.cues.toList(),
                guiTrackData = sourceJson.getAsJsonObject("beats")?.getAsJsonObject(beatId)?.getAsJsonObject("guiTrack")?.deepCopy()
            ).also { editorBeat ->
                beat.actorDirections.forEach { (actorId, direction) ->
                    editorBeat.actorDirections[actorId] = EditorActorDirection(
                        visible = direction.visible,
                        startPose = direction.startPose?.let { sp ->
                            EditorStartPose(position = sp.position, rotation = sp.rotation, blendDurationSeconds = sp.blendDurationSeconds)
                        },
                        startPoseMode = direction.startPoseMode
                    )
                }
                beat.cuts.forEach { (feedId, cutTrack) ->
                    val editorCutTrack = EditorCutTrack()
                    cutTrack.entries.forEach { entry ->
                        editorCutTrack.entries.add(EditorCutEntry(
                            time = entry.time,
                            shotId = entry.shotId,
                            transition = entry.transition
                        ))
                    }
                    editorBeat.cuts[feedId] = editorCutTrack
                }
            }
        }
        scene.transitions.forEach { transition ->
            editor.transitions.add(EditorBeatTransition(
                sourceBeatId = transition.sourceBeatId,
                targetBeatId = transition.targetBeatId,
                condition = transition.condition.getString(),
                transition = transition.transition,
                endsScene = transition.endsScene
            ))
        }
        applyActorTracksFromJson(editor, sourceJson)
        return editor
    }

    fun toScene(editor: EditorScene): Scene {
        return gson.fromJson(toJson(editor), Scene::class.java)
    }

    fun toJson(editor: EditorScene): JsonObject {
        val root = JsonObject()
        root.addProperty("version", editor.version)
        root.addProperty("sceneId", editor.id)
        root.addProperty("startBeatId", editor.startBeatId)

        if (editor.parameters.isNotEmpty()) {
            val params = JsonObject()
            editor.parameters.forEach { (name, def) ->
                params.add(name, parameterToJson(def))
            }
            root.add("parameters", params)
        }

        if (editor.actors.isNotEmpty()) {
            val actors = JsonObject()
            editor.actors.forEach { (id, actor) ->
                val actorObj = JsonObject()
                actorObj.addProperty("id", actor.id)
                actorObj.addProperty("kind", actorKindName(actor.kind))
                actorObj.add("preview", JsonObject().apply {
                    addProperty("species", actor.previewSpecies)
                    addProperty("category", actor.previewCategory)
                    addProperty("scale", actor.previewScale)
                    add("offset", JsonObject().apply {
                        addProperty("x", actor.previewOffset.x)
                        addProperty("y", actor.previewOffset.y)
                        addProperty("z", actor.previewOffset.z)
                    })
                    add("aspects", JsonArray().apply { actor.previewAspects.forEach { add(it) } })
                })
                actors.add(id, actorObj)
            }
            root.add("actors", actors)
        }

        if (editor.shots.isNotEmpty()) {
            val shots = JsonObject()
            editor.shots.forEach { (id, shot) ->
                val shotObj = JsonObject()
                shotObj.addProperty("id", shot.id)
                shotObj.add("position", shot.positionData.deepCopy())
                shotObj.add("rotation", shot.rotationData.deepCopy())
                shot.fovData?.let { shotObj.add("fov", it.deepCopy()) }
                shot.rollData?.let { shotObj.add("roll", it.deepCopy()) }
                shot.lens?.let { lens ->
                    val lensObj = JsonObject()
                    lens.aspectCrop?.let { lensObj.addProperty("aspectCrop", it) }
                    lensObj.addProperty("letterbox", lens.letterbox)
                    shotObj.add("lens", lensObj)
                }
                if (shot.modifiers.isNotEmpty()) {
                    val modArray = JsonArray()
                    shot.modifiers.forEach { modArray.add(it.deepCopy()) }
                    shotObj.add("modifiers", modArray)
                }
                shots.add(id, shotObj)
            }
            root.add("shots", shots)
        }

        if (editor.feeds.isNotEmpty()) {
            val feedArray = JsonArray()
            editor.feeds.forEach { feed ->
                val feedObj = JsonObject()
                feedObj.addProperty("id", feed.id)
                feedObj.add("kind", feedKindToJson(feed.kind))
                feedArray.add(feedObj)
            }
            root.add("feeds", feedArray)
        }

        val beats = JsonObject()
        editor.beats.forEach { (id, beat) ->
            beats.add(id, beatToJson(beat))
        }
        root.add("beats", beats)

        if (editor.transitions.isNotEmpty()) {
            val array = JsonArray()
            editor.transitions.forEach { transition ->
                val obj = JsonObject()
                obj.addProperty("sourceBeatId", transition.sourceBeatId)
                obj.addProperty("targetBeatId", transition.targetBeatId)
                obj.addProperty("condition", transition.condition)
                obj.add("transition", cutTransitionToJson(transition.transition))
                if (transition.endsScene) obj.addProperty("endsScene", true)
                array.add(obj)
            }
            root.add("transitions", array)
        }

        if (editor.damageImmunityDuringTakeover) {
            root.addProperty("damageImmunityDuringTakeover", true)
        }

        if (editor.disconnectPolicy != DisconnectPolicy.END_SCENE) {
            root.addProperty("disconnectPolicy", editor.disconnectPolicy.name)
        }

        return root
    }

    // Runtime tracks do not retain their JSON type discriminator.
    private fun applyActorTracksFromJson(editor: EditorScene, root: JsonObject) {
        val beats = root.getAsJsonObject("beats") ?: return
        editor.beats.forEach { (beatId, editorBeat) ->
            val beatObj = beats.get(beatId)?.asJsonObject ?: return@forEach
            val directions = beatObj.getAsJsonObject("actorDirections") ?: return@forEach
            editorBeat.actorDirections.forEach { (actorId, dir) ->
                dir.tracks.clear()
                val dirObj = directions.get(actorId)?.asJsonObject ?: return@forEach
                val tracksArr = dirObj.getAsJsonArray("tracks") ?: return@forEach
                tracksArr.forEach { el ->
                    val trackObj = el.asJsonObject
                    val type = trackObj.get("trackType")?.asString ?: return@forEach
                    val data = trackObj.deepCopy()
                    data.remove("trackType")
                    dir.tracks.add(EditorTrack(type, data))
                }
            }
        }
    }

    private fun parameterToJson(def: ParameterDefinition): JsonObject {
        val obj = JsonObject()
        obj.addProperty("type", when (def.type) {
            ParameterType.NUMBER -> "number"
            ParameterType.VEC3 -> "vec3"
            ParameterType.STRING -> "string"
        })
        def.description?.let { obj.addProperty("description", it) }
        when (val d = def.default) {
            is ParameterDefault.NumberDefault -> obj.addProperty("default", d.value)
            is ParameterDefault.Vec3Default -> {
                val vec = JsonObject()
                vec.addProperty("x", d.x); vec.addProperty("y", d.y); vec.addProperty("z", d.z)
                obj.add("default", vec)
            }
            is ParameterDefault.StringDefault -> obj.addProperty("default", d.value)
            null -> {}
        }
        return obj
    }

    private fun actorKindName(kind: ActorKind): String = when (kind) {
        ActorKind.PlayerTakeover -> "player_takeover"
        ActorKind.RealEntity -> "real_entity"
        ActorKind.SynthesizedPokemon -> "synthesized_pokemon"
        ActorKind.SynthesizedBedrock -> "synthesized_bedrock"
    }

    private fun feedKindToJson(kind: FeedKind): JsonObject {
        val obj = JsonObject()
        when (kind) {
            FeedKind.Fullscreen -> obj.addProperty("type", "fullscreen")
            is FeedKind.Pip -> {
                obj.addProperty("type", "pip")
                obj.addProperty("corner", kind.corner.name.lowercase())
                obj.addProperty("size", kind.size)
            }
            is FeedKind.Split -> {
                obj.addProperty("type", "split")
                obj.addProperty("side", kind.side.name.lowercase())
                obj.addProperty("size", kind.size)
            }
            is FeedKind.Diagonal -> {
                obj.addProperty("type", "diagonal")
                obj.addProperty("half", kind.half.name.lowercase())
            }
        }
        return obj
    }

    private fun beatToJson(beat: EditorBeat): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", beat.id)
        if (beat.cues.isNotEmpty()) obj.add("cues", JsonArray().apply {
            beat.cues.sortedBy { it.time }.forEach { cue ->
                add(JsonObject().apply { addProperty("id", cue.id); addProperty("time", cue.time) })
            }
        })
        beat.guiTrackData?.let { obj.add("guiTrack", it.deepCopy()) }
        beat.duration?.let { obj.addProperty("duration", it) }
        if (beat.isLooping) obj.addProperty("isLooping", true)
        if (beat.speed != 1.0f) obj.addProperty("speed", beat.speed)
        if (!beat.renderPlayerBody) obj.addProperty("renderPlayerBody", false)

        if (beat.actorDirections.isNotEmpty()) {
            val directions = JsonObject()
            beat.actorDirections.forEach { (actorId, direction) ->
                val dirObj = JsonObject()
                direction.visible?.let { dirObj.addProperty("visible", it) }
                direction.startPose?.let { sp ->
                    val poseObj = JsonObject()
                    poseObj.add("position", positionExpressionToJson(sp.position))
                    val rotObj = JsonObject()
                    rotObj.addProperty("yaw", sp.rotation.yaw)
                    rotObj.addProperty("pitch", sp.rotation.pitch)
                    poseObj.add("rotation", rotObj)
                    poseObj.addProperty("blendDurationSeconds", sp.blendDurationSeconds)
                    dirObj.add("startPose", poseObj)
                }
                direction.startPoseMode?.let { dirObj.addProperty("startPoseMode", it.name.uppercase()) }
                if (direction.tracks.isNotEmpty()) {
                    val tracks = JsonArray()
                    direction.tracks.forEach { editorTrack ->
                        val trackJson = editorTrack.data.deepCopy()
                        trackJson.addProperty("trackType", editorTrack.trackType)
                        tracks.add(trackJson)
                    }
                    dirObj.add("tracks", tracks)
                }
                directions.add(actorId, dirObj)
            }
            obj.add("actorDirections", directions)
        }

        if (beat.cuts.isNotEmpty()) {
            val cuts = JsonObject()
            beat.cuts.forEach { (feedId, cutTrack) ->
                if (cutTrack.entries.isEmpty()) return@forEach
                val cutObj = JsonObject()
                val entryArray = JsonArray()
                cutTrack.entries.forEach { entry ->
                    val entryObj = JsonObject()
                    entryObj.addProperty("time", entry.time)
                    entryObj.addProperty("shotId", entry.shotId)
                    entryObj.add("transition", cutTransitionToJson(entry.transition))
                    entryArray.add(entryObj)
                }
                cutObj.add("entries", entryArray)
                cuts.add(feedId, cutObj)
            }
            if (cuts.size() > 0) obj.add("cuts", cuts)
        }

        if (beat.hiddenHudElements.isNotEmpty()) {
            if (beat.hiddenHudElements == HudElement.ALL) {
                obj.addProperty("hiddenHudElements", true)
            } else {
                val array = JsonArray()
                beat.hiddenHudElements.forEach { array.add(it.id) }
                obj.add("hiddenHudElements", array)
            }
        }

        return obj
    }

    private fun cutTransitionToJson(transition: CutTransition): JsonObject {
        val obj = JsonObject()
        when (transition) {
            CutTransition.Instant -> obj.addProperty("type", "instant")
            is CutTransition.Blend -> {
                obj.addProperty("type", "blend")
                obj.addProperty("duration", transition.duration)
            }
        }
        return obj
    }

    private fun positionExpressionToJson(position: PositionExpression): JsonObject {
        val obj = JsonObject()
        obj.add("x", coordinateValueToJson(position.x))
        obj.add("y", coordinateValueToJson(position.y))
        obj.add("z", coordinateValueToJson(position.z))
        return obj
    }

    private fun coordinateValueToJson(coord: CoordinateValue): JsonElement {
        return when (coord) {
            is CoordinateValue.Static -> JsonPrimitive(coord.value)
            is CoordinateValue.Dynamic -> JsonPrimitive(coord.expression.getString())
        }
    }
}
