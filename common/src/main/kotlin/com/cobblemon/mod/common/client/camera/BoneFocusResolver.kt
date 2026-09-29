/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.BoneTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.exp

class BoneFocusResolver(private val entityId: Int, private val boneName: String) : AutoCloseable {
    private val heldBones = mutableSetOf<String>()
    private var lastPosition: Vec3? = null
    private var velocity = Vec3.ZERO
    private var model: PosableModel? = null
    private var modelBones: Set<String> = emptySet()
    private var starTracker: StarBoneTracker? = null
    private var closed = false

    fun resolve(offset: Vec3, smooth: Boolean, fallback: Vec3?, deltaSeconds: Float): Vec3? {
        if (closed) return lastPosition ?: fallback
        val entity = Minecraft.getInstance().level?.getEntity(entityId)
        if (entity == null || !entity.isAlive || entity.isRemoved) {
            track(emptySet())
            return lastPosition ?: fallback
        }
        val position = when (entity) {
            is PokemonEntity -> resolvePokemonBone(entity)
            else -> resolveGenericEntityBone(entity)
        }?.add(offset) ?: return lastPosition ?: fallback
        if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) return lastPosition ?: fallback
        val previous = lastPosition
        if (!smooth || previous == null) {
            velocity = Vec3.ZERO
            lastPosition = position
            return position
        }

        val dt = deltaSeconds.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 0.0
        // Exact critically damped motion keeps a stationary target independent of frame subdivision.
        val omega = 2.0 / 0.25
        val decay = exp(-omega * dt)
        val delta = previous.subtract(position)
        val step = velocity.add(delta.scale(omega)).scale(dt)
        velocity = velocity.subtract(step.scale(omega)).scale(decay)
        return position.add(delta.add(step).scale(decay)).also { lastPosition = it }
    }

    private fun resolvePokemonBone(pokemon: PokemonEntity): Vec3? {
        val state = pokemon.delegate as? PokemonClientDelegate ?: return null
        val currentModel = state.currentModel
        if (model !== currentModel) {
            track(emptySet())
            model = currentModel
            val descendants = mutableMapOf<String, Int>()
            currentModel?.let { collectBones(it.rootPart, descendants) }
            modelBones = descendants.keys.toSet()
            starTracker = currentModel?.let { StarBoneTracker(descendants) }
        }

        if (boneName.lowercase() in STAR_BONE_ALIASES) {
            val tracker = starTracker
            val selected = tracker?.starBone
            track(if (selected == null) modelBones else setOf(selected).intersect(modelBones))
            val name = tracker?.sample(state)
            if (name != null) {
                track(setOf(name).intersect(modelBones))
                return state.locatorStates[name]?.getOrigin()
                    ?: state.trackedBoneStates.takeIf { state.trackedBoneModel === currentModel }?.get(name)?.getOrigin()
                    ?: state.locatorStates["middle"]?.getOrigin()
            }
            return state.locatorStates["middle"]?.getOrigin()
        }

        if (boneName.lowercase() in SEND_OUT_BALL_ALIASES) {
            track(emptySet())
            return if (state.ballDone) {
                state.locatorStates["middle"]?.getOrigin() ?: pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
            } else {
                state.sendOutPosition?.add(state.sendOutOffset ?: Vec3.ZERO)
                    ?: pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
            }
        }

        val locator = state.locatorStates[boneName]
            ?: state.locatorStates[boneName.lowercase()]
            ?: state.locatorStates[boneName.replace("_", "")]
            ?: state.locatorStates["internal_locator__$boneName"]
        if (locator != null) {
            track(emptySet())
            return locator.getOrigin()
        }

        track(setOf(boneName))
        val tracked = state.trackedBoneStates.takeIf { currentModel != null && state.trackedBoneModel === currentModel }?.get(boneName)
        if (tracked != null) return tracked.getOrigin()
        return BONE_TO_LOCATOR_FALLBACKS[boneName.lowercase()]?.let { state.locatorStates[it]?.getOrigin() }
            ?: state.locatorStates["middle"]?.getOrigin()
    }

    private fun collectBones(root: Bone, descendants: MutableMap<String, Int>): Int {
        var count = 0
        for ((name, child) in root.children) {
            val childCount = collectBones(child, descendants)
            descendants[name] = childCount
            count += childCount + 1
        }
        return count
    }

    private fun resolveGenericEntityBone(entity: Entity): Vec3 {
        val height = when (boneName.lowercase()) {
            "head", "head_ai_target_position" -> 0.9
            "neck" -> 0.7
            else -> 0.5
        }
        return entity.position().add(0.0, entity.bbHeight * height, 0.0)
    }

    private fun track(names: Set<String>) {
        (heldBones - names).forEach { BoneTracker.release(entityId, it) }
        (names - heldBones).forEach { BoneTracker.hold(entityId, it) }
        heldBones.clear()
        heldBones.addAll(names)
    }

    override fun close() {
        track(emptySet())
        model = null
        modelBones = emptySet()
        starTracker = null
        closed = true
    }

    companion object {
        private val STAR_BONE_ALIASES = setOf("*", "star", "auto", "dynamic")
        private val SEND_OUT_BALL_ALIASES = setOf("send_out_ball", "ball", "pokeball")
        private val BONE_TO_LOCATOR_FALLBACKS = mapOf(
            "body" to "middle", "body_0" to "middle", "torso" to "middle", "chest" to "middle",
            "spine" to "middle", "head_ai_target_position" to "head", "base" to "root", "origin" to "root"
        )
    }
}
