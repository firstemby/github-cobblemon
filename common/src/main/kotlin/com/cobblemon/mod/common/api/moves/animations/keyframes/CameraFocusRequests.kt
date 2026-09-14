/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.runtime.SceneInstance
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import java.util.UUID
import net.minecraft.world.entity.Entity

internal object CameraFocusRequests {
    private data class Request(val owner: ActionEffectContext, val entity: Entity, val target: Boolean)
    private class SceneRequests(val scene: SceneInstance) {
        val requests = linkedMapOf<UUID, Request>()

        fun apply() {
            val latest = requests.values.lastOrNull()
            if (latest != null) {
                scene.updateActorBinding(if (latest.target) "target" else "user", ActorBinding.Real(latest.entity))
                scene.setVariable("camera_focus_role", if (latest.target) 1.0 else 0.0)
            }
            scene.setVariable("camera_focus_count", requests.size.toDouble())
        }
    }

    private val scenes = mutableMapOf<UUID, SceneRequests>()

    fun acquire(scene: SceneInstance, owner: ActionEffectContext, entity: Entity, target: Boolean): AutoCloseable? {
        val actorId = if (target) "target" else "user"
        if (!scene.isActive() || scene.scene.actors[actorId]?.kind != ActorKind.RealEntity) return null
        val sceneId = scene.id
        val state = scenes.getOrPut(sceneId) {
            scene.onStop { scenes.remove(sceneId) }
            SceneRequests(scene)
        }
        val id = UUID.randomUUID()
        state.requests[id] = Request(owner, entity, target)
        try {
            state.apply()
        } catch (error: Exception) {
            state.requests.remove(id)
            throw error
        }
        // The timeout keeps only IDs, so stopping a scene releases its actors and contexts immediately.
        return AutoCloseable {
            val current = scenes[sceneId]
            if (current?.requests?.remove(id) != null) current.apply()
        }
    }

    fun release(owner: ActionEffectContext) {
        for (state in scenes.values) {
            if (state.requests.entries.removeIf { it.value.owner === owner }) state.apply()
        }
    }
}
