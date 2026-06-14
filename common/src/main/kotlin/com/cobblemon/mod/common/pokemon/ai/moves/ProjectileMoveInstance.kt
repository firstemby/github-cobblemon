/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.entity.RealtimeMoveProjectile
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt


class ProjectileMoveInstance(
    val projectileMove: ProjectileMove,
    pokemonEntity: PokemonEntity,
    targetEntity: LivingEntity,
    parentEntity: Entity? = null,
) :
    RealTimeMoveInstance(move = projectileMove, pokemonEntity = pokemonEntity, targetEntity, parentEntity) {
    var salvosRemaining = projectileMove.salvoCount
    var salvoCooldownTicks = 0

    override fun start() {
        pokemonEntity.navigation.stop()
    }

    override fun tick(): Boolean {
        if (delayTicks > 0) {
            delayTicks--
            return true
        }

        if (salvosRemaining > 0) {
            if (salvoCooldownTicks <= 0) {
                createProjectiles()
                salvosRemaining--
                salvoCooldownTicks = projectileMove.salvoDelayTicks
            } else {
                salvoCooldownTicks--
            }
            return true
        }


        if (recoveryTicks > 0) {
            recoveryTicks--
            return true
        }

        return false
    }

    override fun end() {
        // TODO: add this as an "on hit" post event on the project
        var attackTargetData = pokemonEntity.biggerBrain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
        if (attackTargetData != null) {
            attackTargetData.hitsLanded += 1

        } else {
            attackTargetData = CobblemonAttackTargetData()
            attackTargetData.hitsLanded = 1
        }
        pokemonEntity.biggerBrain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
        super.end()
    }


    fun createProjectiles() {

        val baseVelocity = if (projectileMove.targetOpponent) {
            targetEntity.eyePosition.subtract(pokemonEntity.eyePosition).normalize()
        } else {
            this.rotation
        }
        val positionOffset = calculatePositionOffset(baseVelocity)
        val startPos = (parentEntity?.eyePosition ?: pokemonEntity.eyePosition).add(positionOffset)
        for (i in 1..projectileMove.shotsPerSalvo) {

            var velocity = baseVelocity
            if (projectileMove.spreadDegrees > 0) {
                velocity = getRandomizedVector(velocity, projectileMove.spreadDegrees)
            }

            val projectile = RealtimeMoveProjectile(
                level = pokemonEntity.level(),
                owner = pokemonEntity,
                this
            )
            projectile.setPos(startPos)
            projectile.deltaMovement = velocity.scale(projectileMove.speed.toDouble())
            val yaw =
                (atan2(projectile.deltaMovement.z, projectile.deltaMovement.x) * 180.0 / Math.PI).toFloat() - 90.0f

            // TODO clean this up
            val pitch = (-(atan2(
                projectile.deltaMovement.y,
                sqrt(projectile.deltaMovement.x * projectile.deltaMovement.x + projectile.deltaMovement.z * projectile.deltaMovement.z)
            ) * 180.0 / Math.PI)).toFloat()
            projectile.yRotO = yaw
            projectile.xRotO = pitch
            projectile.yRot = yaw
            projectile.xRot = pitch
            pokemonEntity.level().addFreshEntity(projectile)

        }

    }

    fun calculatePositionOffset(dir: Vec3): Vec3 {
        val forward: Vec3 = dir.normalize()
        val worldUp = Vec3(0.0, 1.0, 0.0)

        val right: Vec3 = forward.cross(worldUp).normalize()
        val up = right.cross(forward).normalize()

        return right.scale(this.positionOffset.x)
            .add(up.scale(this.positionOffset.y))
            .add(forward.scale(this.positionOffset.z))
    }

}
