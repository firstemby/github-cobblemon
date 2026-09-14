/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.registry

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader

/** Scenes load only on the server, from data/<namespace>/cinematic_scenes/<id>.json. */
object SceneRegistry : DataRegistry {
    override val id = cobblemonResource("cinematic_scenes")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<SceneRegistry>()

    private val gson = GsonBuilder()
        .registerTypeAdapter(Scene::class.java, SceneDeserializer)
        .create()

    private val scenes = mutableMapOf<String, Scene>()
    private val sources = mutableMapOf<String, String>()

    override fun reload(manager: ResourceManager) {
        scenes.clear()
        sources.clear()

        val resources = manager.listResources("cinematic_scenes") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources.entries.sortedBy { it.key.toString() }) {
            try {
                resource.open().use { stream ->
                    val source = JsonParser.parseReader(InputStreamReader(stream, Charsets.UTF_8)).asJsonObject
                    val scene = gson.fromJson(source, Scene::class.java)
                    val errors = scene.validate()
                    val hasErrors = errors.any { it.severity == ValidationError.Severity.ERROR }

                    if (hasErrors) {
                        Cobblemon.LOGGER.error("[SceneRegistry] Validation failed for '$identifier':")
                        errors.forEach { Cobblemon.LOGGER.error("  [$it]") }
                        return@use
                    }

                    if (errors.isNotEmpty()) {
                        Cobblemon.LOGGER.warn("[SceneRegistry] Warnings for scene '${scene.id}':")
                        errors.forEach { Cobblemon.LOGGER.warn("  $it") }
                    }

                    if (scenes.containsKey(scene.id)) {
                        Cobblemon.LOGGER.warn("[SceneRegistry] Duplicate scene id '${scene.id}' in $identifier, overwriting")
                    }
                    scenes[scene.id] = scene
                    sources[scene.id] = source.toString()
                    Cobblemon.LOGGER.debug("[SceneRegistry] Loaded scene '${scene.id}' from $identifier")
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[SceneRegistry] Error loading scene from $identifier", e)
            }
        }

        Cobblemon.LOGGER.info("[SceneRegistry] Loaded ${scenes.size} scenes")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
    }

    fun getScene(id: String): Scene? = scenes[id]
    fun getSource(id: String): String? = sources[id]
    fun getAllScenes(): Collection<Scene> = scenes.values

}
