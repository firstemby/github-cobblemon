/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3

class WaypointActorMotion(val track: WaypointPathfinding, private val entity: Entity) {
    private val originalGravity = entity.isNoGravity
    private val originalVelocity = entity.deltaMovement
    private val originalAi = (entity as? Mob)?.isNoAi
    private val originalRecalculation = (entity as? PokemonEntity)?.enablePoseTypeRecalculation
    private val originalPose = when (entity) {
        is PokemonEntity -> entity.entityData.get(PokemonEntity.POSE_TYPE)
        is GenericBedrockEntity -> entity.entityData.get(GenericBedrockEntity.POSE_TYPE)
        else -> null
    }
    private val originalMoving = (entity as? PokemonEntity)?.entityData?.get(PokemonEntity.MOVING)
    private val terrain = LevelWaypointTerrain(entity.level(), entity.bbWidth.toDouble().coerceAtLeast(0.1),
        entity.bbHeight.toDouble().coerceAtLeast(0.1))
    var playback = newPlayback()
        private set
    private var lastTime = 0f

    private fun newPlayback() = WaypointPlayback(track, NativeWaypointMotor(track, entity))

    init {
        if (entity is Mob) {
            entity.navigation.stop()
            entity.isNoAi = true
        }
        if (entity is PokemonEntity) entity.enablePoseTypeRecalculation = false
    }

    fun sample(time: Float) {
        if (time < lastTime) {
            playback.close()
            playback = newPlayback()
        }
        lastTime = time
        playback.sample(time)
        if (playback.error == null || terrain.clear(playback.position)) {
            val position = playback.position
            if (entity is ServerPlayer) entity.connection.teleport(position.x, position.y, position.z, playback.yaw, 0f)
            else entity.setPos(position)
            entity.yRot = playback.yaw
            entity.xRot = 0f
            if (entity is LivingEntity) {
                entity.yBodyRot = playback.yaw
                entity.yHeadRot = playback.yaw
            }
            entity.setOnGround(playback.onGround)
        }
        when (entity) {
            is PokemonEntity -> {
                entity.entityData.set(PokemonEntity.POSE_TYPE, playback.pose)
                entity.entityData.set(PokemonEntity.MOVING, playback.pose in PoseType.MOVING_POSES)
            }
            is GenericBedrockEntity -> entity.entityData.set(GenericBedrockEntity.POSE_TYPE, playback.pose)
        }
        entity.isNoGravity = true
        entity.deltaMovement = Vec3.ZERO
    }

    fun restore() {
        restoreState { playback.close() }
        when (entity) {
            is PokemonEntity -> {
                originalPose?.let { restoreState { entity.entityData.set(PokemonEntity.POSE_TYPE, it) } }
                originalMoving?.let { restoreState { entity.entityData.set(PokemonEntity.MOVING, it) } }
                originalRecalculation?.let { restoreState { entity.enablePoseTypeRecalculation = it } }
            }
            is GenericBedrockEntity -> originalPose?.let { restoreState { entity.entityData.set(GenericBedrockEntity.POSE_TYPE, it) } }
        }
        originalAi?.let { restoreState { (entity as Mob).isNoAi = it } }
        restoreState { entity.isNoGravity = originalGravity }
        restoreState { entity.deltaMovement = originalVelocity }
    }

    private inline fun restoreState(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not restore cinematic actor motion", error)
        }
    }
}
