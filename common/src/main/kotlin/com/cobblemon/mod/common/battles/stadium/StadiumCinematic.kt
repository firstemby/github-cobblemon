/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

object StadiumCinematic {
    class ValidationException(val reason: Component) : IllegalArgumentException(reason.string)

    const val MAX_LENGTH = 262144
    private val gson = GsonBuilder().registerTypeAdapter(Scene::class.java, SceneDeserializer).create()

    fun fitArena(scene: Scene, arena: StadiumArena?): Scene = scene.copy(shots = scene.shots.mapValues { (_, shot) ->
        val orbit = shot.position as? OrbitPosition
        if (orbit?.fitArena == true) shot.copy(position = orbit.copy(center = arena?.center ?: orbit.center,
            arenaWidth = arena?.width?.toFloat(), arenaDepth = arena?.depth?.toFloat())) else shot
    })

    fun forEditor(source: String, data: StadiumBlockData, origin: Vec3): String {
        val root = JsonParser.parseString(source).asJsonObject
        val actors = root.getAsJsonObject("actors") ?: JsonObject().also { root.add("actors", it) }
        val previews = actors.entrySet().associate { it.key to it.value.asJsonObject.getAsJsonObject("preview")?.deepCopy() }
        val layout = data.layout(data.cinematicLayout)
        val arena = data.arena
        root.getAsJsonObject("shots")?.entrySet()?.forEach { (_, value) ->
            val position = value.asJsonObject.getAsJsonObject("position")
            if (position?.get("fitArena")?.asBoolean == true) {
                position.remove("arenaWidth"); position.remove("arenaDepth")
                if (arena != null) {
                    position.add("center", vector(arena.center))
                    position.addProperty("arenaWidth", arena.width)
                    position.addProperty("arenaDepth", arena.depth)
                }
            }
        }
        if (layout != null) {
            actors.entrySet().filter { it.key.startsWith("trainer_") || it.key.startsWith("pokemon_") }
                .forEach { it.value.asJsonObject.remove("preview") }
            listOf(layout.side1, layout.side2).forEachIndexed { sideIndex, side ->
                fun add(id: String, placement: StadiumBlockPlacement, species: String) {
                    val actor = actors.getAsJsonObject(id) ?: JsonObject().apply {
                        addProperty("id", id); addProperty("kind", "real_entity"); actors.add(id, this)
                    }
                    actor.add("preview", (previews[id] ?: JsonObject()).apply {
                        if (get("species")?.asString.isNullOrBlank()) addProperty("species", species)
                        add("offset", vector(placement.position.subtract(origin)))
                    })
                }
                side.actors.forEachIndexed { index, placement ->
                    add("trainer_${sideIndex + 1}" + if (index == 0) "" else "_${index + 1}", placement,
                        if (sideIndex == 0) "pikachu" else "eevee")
                }
                side.pokemon.forEachIndexed { index, placement ->
                    add("pokemon_${sideIndex + 1}${'a' + index}", placement, if (sideIndex == 0) "charizard" else "blastoise")
                }
                side.pokemon.firstOrNull()?.let { add(if (sideIndex == 0) "user" else "target", it,
                    if (sideIndex == 0) "charizard" else "blastoise") }
            }
        }
        return root.toString()
    }

    private fun vector(position: Vec3) = JsonObject().apply {
        addProperty("x", position.x); addProperty("y", position.y); addProperty("z", position.z)
    }

    fun resolve(id: ResourceLocation?, source: String?): Scene? {
        if (id == null) return null
        return if (source == null) SceneRegistry.getScene(id.toString()) else runCatching {
            parse(source).takeIf { it.id == id.toString() }
        }.getOrNull()
    }

    fun parse(source: String): Scene {
        if (source.length > MAX_LENGTH) throw ValidationException(lang("stadium.cinematic.too_large"))
        val scene = gson.fromJson(source, Scene::class.java)
        if (scene.validate().any { it.severity == ValidationError.Severity.ERROR }) {
            throw ValidationException(lang("stadium.cinematic.validation_errors"))
        }
        if (!hasReachableLaunchCue(scene)) {
            throw ValidationException(lang("stadium.cinematic.launch_cue"))
        }
        return scene
    }

    fun hasReachableLaunchCue(scene: Scene): Boolean {
        val launchBeats = scene.beats.values.flatMap { beat -> beat.cues.filter { it.id == "battle_launch" }.map { beat.id } }
        if (launchBeats.size != 1) return false
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(scene.startBeatId)
        while (pending.isNotEmpty()) {
            val beatId = pending.removeFirst()
            if (!visited.add(beatId)) continue
            if (beatId == launchBeats.single()) return true
            scene.getTransitionsFrom(beatId).filter { !it.endsScene }.forEach { pending.add(it.targetBeatId) }
        }
        return false
    }

    fun normalize(source: String, id: ResourceLocation): String {
        if (source.length > MAX_LENGTH) throw ValidationException(lang("stadium.cinematic.too_large"))
        val root = JsonParser.parseString(source).asJsonObject
        root.addProperty("sceneId", id.toString())
        return root.toString().also { parse(it) }
    }
}
