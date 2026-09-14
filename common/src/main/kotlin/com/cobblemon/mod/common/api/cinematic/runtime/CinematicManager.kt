/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CinematicManager {

    private val scenes: ConcurrentHashMap<UUID, SceneInstance> = ConcurrentHashMap()

    private data class SceneCounterKey(val sceneId: UUID, val name: String)

    private class SceneCounterState(val scene: SceneInstance, val name: String) {
        val owners = mutableSetOf<UUID>()

        fun apply() {
            scene.setVariable(name, owners.size.toDouble())
        }
    }

    private val sceneCounters = mutableMapOf<SceneCounterKey, SceneCounterState>()
    private val battleToScene: ConcurrentHashMap<UUID, UUID> = ConcurrentHashMap()

    /** Register viewers before starting so synthesized actors spawn with a usable viewer scope. */
    fun startScene(
        scene: Scene,
        origin: Vec3,
        actorBindings: Map<String, ActorBinding> = emptyMap(),
        parameterOverrides: Map<String, Any> = emptyMap(),
        initialViewers: Set<UUID> = emptySet(),
        previewMode: Boolean = false
    ): UUID? {
        var instance: SceneInstance? = null
        try {
            val created = SceneInstance(
                id = UUID.randomUUID(),
                scene = scene,
                origin = origin,
                actorBindings = actorBindings,
                parameterOverrides = parameterOverrides,
                previewMode = previewMode
            )
            instance = created
            initialViewers.forEach(created::addViewer)
            if (!created.start()) {
                created.stop()
                return null
            }
        } catch (error: Exception) {
            instance?.stop()
            Cobblemon.LOGGER.error("Failed to start cinematic scene '{}'", scene.id, error)
            return null
        }
        val startedInstance = instance ?: return null
        scenes[startedInstance.id] = startedInstance
        return startedInstance.id
    }

    fun startSceneById(
        sceneDefinitionId: String,
        origin: Vec3,
        actorBindings: Map<String, ActorBinding> = emptyMap(),
        parameterOverrides: Map<String, Any> = emptyMap(),
        previewMode: Boolean = false
    ): UUID? {
        val scene = SceneRegistry.getScene(sceneDefinitionId) ?: run {
            Cobblemon.LOGGER.error("[CinematicManager] Scene '$sceneDefinitionId' not found in registry")
            return null
        }
        return startScene(scene, origin, actorBindings, parameterOverrides, previewMode = previewMode)
    }

    fun stopScene(sceneId: UUID) {
        scenes.remove(sceneId)?.stop()
    }

    fun getScene(sceneId: UUID): SceneInstance? = scenes[sceneId]

    fun getActiveScenes(): Collection<SceneInstance> = scenes.values

    fun tick() {
        scenes.values.toList().forEach { instance ->
            instance.tick()
            if (!instance.isActive()) scenes.remove(instance.id, instance)
        }
    }

    fun clear() {
        scenes.keys.toList().forEach(::stopScene)
    }

    fun activeCount(): Int = scenes.size

    fun isPlayerUnderImmuneTakeover(playerId: UUID): Boolean {
        return scenes.values.any { it.isPlayerTakeoverImmune(playerId) }
    }

    fun onPlayerDisconnect(playerId: UUID) {
        scenes.values.forEach { it.onPlayerDisconnect(playerId) }
    }

    fun setSceneVariable(sceneId: UUID, name: String, value: Double) {
        scenes[sceneId]?.setVariable(name, value)
    }

    fun acquireSceneCounter(sceneId: UUID, name: String): AutoCloseable? {
        val scene = scenes[sceneId]?.takeIf(SceneInstance::isActive) ?: return null
        val key = SceneCounterKey(sceneId, name)
        val state = sceneCounters.getOrPut(key) {
            scene.onStop { sceneCounters.remove(key) }
            SceneCounterState(scene, name)
        }
        val owner = UUID.randomUUID()
        state.owners += owner
        try {
            state.apply()
        } catch (error: Exception) {
            state.owners -= owner
            if (state.owners.isEmpty()) sceneCounters.remove(key, state)
            throw error
        }
        return AutoCloseable {
            val current = sceneCounters[key]
            if (current === state && current.owners.remove(owner)) current.apply()
        }
    }

    fun setSceneVec3(sceneId: UUID, prefix: String, value: Vec3) {
        val instance = scenes[sceneId] ?: return
        instance.setVariable("${prefix}_x", value.x)
        instance.setVariable("${prefix}_y", value.y)
        instance.setVariable("${prefix}_z", value.z)
    }

    /** Callers must validate that the new binding matches the actor's declared kind. */
    fun updateActorBinding(sceneId: UUID, actorId: String, binding: ActorBinding) {
        scenes[sceneId]?.updateActorBinding(actorId, binding)
    }

    fun registerSceneForBattle(battleId: UUID, sceneId: UUID) {
        val scene = scenes[sceneId]?.takeIf(SceneInstance::isActive)
            ?: throw IllegalArgumentException("Cannot register an inactive cinematic scene")
        battleToScene[battleId] = sceneId
        try {
            scene.onStop { battleToScene.remove(battleId, sceneId) }
        } catch (error: Exception) {
            battleToScene.remove(battleId, sceneId)
            throw error
        }
    }

    fun unregisterSceneForBattle(battleId: UUID) {
        battleToScene.remove(battleId)
    }

    fun sceneForBattle(battleId: UUID): UUID? {
        val sceneId = battleToScene[battleId] ?: return null
        if (scenes[sceneId]?.isActive() == true) return sceneId
        battleToScene.remove(battleId, sceneId)
        return null
    }
}
