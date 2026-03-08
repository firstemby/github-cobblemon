/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FluidState
import kotlin.math.atan2

/**
 * Swimming Pokemon perform a cool little jump out of the water if they're near the surface
 *
 */
class JumpOutOfWater(
    val ticksBetweenAttempts: Int = 60,
) : Objective {
    var inWater = false
    var remainingCooldownTicks = ticksBetweenAttempts
    var fixation: EntityFixation? = null
    override fun getDescription(entity: LivingEntity) = "Jump out of water"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(fixation)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        if (entity.random.nextInt(CHANCE) != 0) {
            return ObjectiveStatus.INACTIVE
        } else {
            if (!brain.distract(idleBoredomDistraction)) return ObjectiveStatus.BLOCKED
            val direction = entity.motionDirection
            val x = direction.stepX
            val z = direction.stepZ
            val blockPos = entity.blockPosition()
            val offsets = OFFSET_MULTIPLIERS
            for (offset in offsets) {
                if (!this.isWater(entity, blockPos, x, z, offset) || !this.isAirAbove(entity, blockPos, x, z, offset)) {
                    return ObjectiveStatus.ACTIVE
                }
            }

            val newVelocity = entity.deltaMovement.add(direction.stepX.toDouble() * 0.6, 0.7, direction.stepZ.toDouble() * 0.6)
            entity.deltaMovement = newVelocity
            entity.getNavigation().stop()
            fixation = brain.fixate(
                fixation = JumpOutOfWaterFixation(
                    this,
                    movementTracker = null
                )
            )
        }
        return ObjectiveStatus.ACTIVE
    }


    class JumpOutOfWaterFixation(
        val objective: JumpOutOfWater,
        override val movementTracker: MovementTracker?,
    ) : EntityFixation, MovementTrackerFixation {

        override fun getDescription(entity: LivingEntity) = "Jumping out of water"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return distraction.isDamage || distraction.isForce
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {}

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            val bl: Boolean = objective.inWater
            if (!bl) {
                val fluidState: FluidState = entity.level().getFluidState(entity.blockPosition())
                objective.inWater = fluidState.`is`(FluidTags.WATER)
            }

            if (objective.inWater && !bl) {
                // Transition tick from in water to out of water
                entity.playSound(SoundEvents.DOLPHIN_JUMP, 1.0f, 1.0f)
            }

            val vec3d = entity.deltaMovement
            if (vec3d.y * vec3d.y < 0.029999999329447746 && entity.xRot != 0.0f) {
                entity.xRot = Mth.rotLerp(0.2f, entity.xRot, 0.0f)
            } else if (vec3d.length() > 9.999999747378752E-6) {
                val d = vec3d.horizontalDistance()
                val e = atan2(-vec3d.y, d) * 57.2957763671875
                entity.xRot = e.toFloat()
            }

            val d: Double = vec3d.y
            return (!(d * d < 0.029999999329447746) || entity.xRot == 0.0f || !(Math.abs(entity.xRot) < 10.0f) || !entity.isInWater) && !entity.onGround()

        }
    }

    class Config : ObjectiveConfig {
        val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            ticksBetweenAttempts,
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return JumpOutOfWater(
                ticksBetweenAttempts = ticksBetweenAttempts,
            )
        }
    }

    private fun isWater(entity: PokemonEntity, pos: BlockPos, offsetX: Int, offsetZ: Int, multiplier: Int): Boolean {
        val blockPos = pos.offset(offsetX * multiplier, 0, offsetZ * multiplier)
        return entity.level().getFluidState(blockPos).`is`(FluidTags.WATER) && !entity.level().getBlockState(blockPos)
            .blocksMotion()
    }

    private fun isAirAbove(entity: PokemonEntity, pos: BlockPos, offsetX: Int, offsetZ: Int, multiplier: Int): Boolean {
        return entity.level().getBlockState(pos.offset(offsetX * multiplier, 1, offsetZ * multiplier))
            .isAir && entity.level().getBlockState(pos.offset(offsetX * multiplier, 2, offsetZ * multiplier))
            .isAir
    }

    companion object {
        private const val CHANCE = 10
        private val OFFSET_MULTIPLIERS = intArrayOf(0, 1, 4, 5, 6, 7)
    }
}