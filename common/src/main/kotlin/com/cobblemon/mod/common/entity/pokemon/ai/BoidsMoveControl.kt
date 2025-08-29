/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai


import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity.Companion.POSE_TYPE
import com.cobblemon.mod.common.util.math.geometry.toDegrees
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.min
import kotlin.math.pow

class BoidsMoveControl(val pokemonEntity: PokemonEntity, val target: LivingEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3
    }

    var speed: Double? = null

    override fun tick() {

        if (pokemonEntity.horizontalCollision) {
            pokemonEntity.setYRot(pokemonEntity.getYRot() + 180.0f)
            this.speed = 0.1
        }
        val speedLimit = 0.5
        //pokemonEntity.entityData.set(POSE_TYPE, PoseType.FLY)
        pokemonEntity.setFlying(true)
        // Just so I can test stuff
        pokemonEntity.resetFallDistance()
        // ignore all the friction stuff from outside
        speed?.let { pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.normalize().scale(it)) }
        val orbitRadius = 16.0
        val targetPos = target.position().add(0.0, 4.0, 0.0)
        var toTargetVec = targetPos.subtract(pokemonEntity.position())
        val distToTarget = toTargetVec.length()

        val desiredScale = speedLimit
        val desiredVec = toTargetVec.normalize().scale(desiredScale)

        val maxForce = 0.02
        var steer = desiredVec.subtract(pokemonEntity.deltaMovement)
        if (steer.length() > maxForce) {
            steer = steer.normalize().scale(maxForce)
        }
        // Apply steering force to current movement
        pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.add(steer))
        speed = pokemonEntity.deltaMovement.length()



        // Now set rotation to what it needs to be
        val normVec = pokemonEntity.deltaMovement.normalize()
        pokemonEntity.yRot = Mth.atan2(normVec.z, normVec.x).toDegrees() - 90.0f
        pokemonEntity.xRot = asin(normVec.y.toFloat()).toDegrees()
    }
}

//val desiredScale = if (distToTarget > slowDownRadius) speedLimit else (distToTarget/slowDownRadius).pow(2) * speedLimit
//desiredVec = desiredVec.normalize().scale(desiredScale)