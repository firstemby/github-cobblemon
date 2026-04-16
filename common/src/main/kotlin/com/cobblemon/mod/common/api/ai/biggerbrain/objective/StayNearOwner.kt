/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import kotlin.math.abs
import kotlin.math.ceil
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator

/**
 * Keeps track of the owner entity, and if they are in the same world but are too far away, fixates on getting closer.
 * If they are extremely far away, will attempt to teleport to them. When walking to the owner, it pauses for a short
 * duration after reaching them to look at them. It's cute.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class StayNearOwner(
    val ticksBetweenAttempts: Int = 60,
    val completionRange: Expression = "4.0".asExpression(),
    val maxDistance: Expression = "14.0".asExpression(),
    val teleportDistance: Expression = "24.0".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression()
) : Objective {
    var followTask: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts

    override fun getDescription(entity: LivingEntity) = "Stay near owner"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        return ObjectiveStatus.BLOCKED
//        if (entity !is TamableAnimal) {
//            return ObjectiveStatus.INVALID
//        } else if (brain.isFixation(followTask)) {
//            remainingCooldownTicks = ticksBetweenAttempts
//            return ObjectiveStatus.FIXATED
//        }
//
//        val owner = entity.owner ?: return ObjectiveStatus.INVALID
//        if (owner.level() != entity.level()) {
//            return ObjectiveStatus.INVALID
//        }
//
//        if (remainingCooldownTicks > 0) {
//            remainingCooldownTicks--
//        }
//
//        mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
//
//        val teleportDistance = mainThreadRuntime.resolveFloat(teleportDistance)
//        val maxDistance = mainThreadRuntime.resolveFloat(maxDistance)
//        val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)
//        val completionRange = mainThreadRuntime.resolveInt(completionRange)
//
//        val horizontalHitboxRadius = maxOf(entity.boundingBox.xsize, entity.boundingBox.zsize) / 2
//
//        val distance = entity.distanceTo(owner) - horizontalHitboxRadius
//        if (distance > teleportDistance) {
//            if (remainingCooldownTicks <= 0 && brain.distract(Distraction.FORCE)) {
//                remainingCooldownTicks = ticksBetweenAttempts
//                tryTeleport(entity, owner)
//                return ObjectiveStatus.ACTIVE
//            }
//            return if (remainingCooldownTicks <= 0) ObjectiveStatus.BLOCKED else ObjectiveStatus.ACTIVE
//        } else if (distance > maxDistance) {
//            if (remainingCooldownTicks <= 0 && brain.distract(comfortDistraction)) {
//                remainingCooldownTicks = ticksBetweenAttempts
//                val movementTracker = RegularMovementTracker.attempt(
//                    entity = entity,
//                    walkTarget = WalkTarget(
//                        owner,
//                        speedMultiplier,
//                        maxOf(completionRange, ceil(horizontalHitboxRadius).toInt()) - 1
//                    )
//                )
//                if (movementTracker != null) {
//                    followTask = brain.fixate(
//                        fixation = FollowOwnerFixation(
//                            entity = entity,
//                            owner = owner,
//                            teleportDistance = teleportDistance,
//                            movementTracker = movementTracker
//                        )
//                    )
//                }
//            }
//        }
//        return ObjectiveStatus.ACTIVE
    }

    private fun tryTeleport(entity: TamableAnimal, owner: Entity): Boolean {
        val blockPos = owner.blockPosition()
        for (i in 0..9) {
            val j = this.getRandomInt(entity.random, -3, 3)
            val k = this.getRandomInt(entity.random, -1, 1)
            val l = this.getRandomInt(entity.random, -3, 3)
            val succeeded = this.tryTeleportTo(entity, owner, blockPos.x + j, blockPos.y + k, blockPos.z + l)
            if (succeeded) {
                return true
            }
        }
        return false
    }

    private fun tryTeleportTo(entity: TamableAnimal, owner: Entity, x: Int, y: Int, z: Int): Boolean {
        if (abs(x.toDouble() - owner.x) < 2.0 && abs(z - owner.z) < 2.0) {
            return false
        } else if (!this.canTeleportTo(entity, BlockPos(x, y, z))) {
            return false
        } else {
            entity.moveTo(
                x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5,
                entity.yRot,
                entity.xRot
            )
            entity.navigation.stop()
            return true
        }
    }

    private fun canTeleportTo(entity: TamableAnimal, pos: BlockPos): Boolean {
        val pathNodeType = WalkNodeEvaluator.getPathTypeStatic(entity, pos.mutable())
        if (pathNodeType != PathType.WALKABLE) {
            return false // Could be more complex to support fliers and swimmers
        } else {
            val blockPos = pos.subtract(entity.blockPosition())
            return entity.level().noCollision(entity, entity.boundingBox.move(blockPos))
        }
    }

    private fun getRandomInt(random: RandomSource, min: Int, max: Int) = random.nextInt(max - min + 1) + min

    class FollowOwnerFixation(
        entity: PathfinderMob,
        val owner: LivingEntity,
        val teleportDistance: Float,
        override val movementTracker: MovementTracker,
    ): EntityFixation, LookingTrackerFixation, MovementTrackerFixation {

        // Look at the owner for some amount of time after reaching them. For cuteness!
        var lingeringStareTicks = (20..40).random()

        override val lookingTracker: LookingTracker = LookingTracker(entity = entity, lookTarget = EntityTracker(owner, true))
        override fun getDescription(entity: LivingEntity) = "Moving to owner"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            // Easily distracted once we've reached the player.
            return distraction.isDamage || distraction.isForce || movementTracker.lastStatus == MovementStatus.COMPLETED
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            lookingTracker.tick()

            if (entity.distanceTo(owner) > teleportDistance) {
                // Too far away, give up, the teleport will handle it
                return false
            }

            if (movementTracker.lastStatus == MovementStatus.COMPLETED) {
                lingeringStareTicks--
                return lingeringStareTicks > 0
            }

            return tickMovementFixation() != MovementStatus.FAILED
        }
    }

    class Config : ObjectiveConfig {
        val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val completionRange: ExpressionOrEntityVariable = Either.left("4.0".asExpression())
        val maxDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val teleportDistance: ExpressionOrEntityVariable = Either.left("24.0".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            ticksBetweenAttempts,
            completionRange,
            maxDistance,
            teleportDistance,
            speedMultiplier
        ).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return StayNearOwner(
                ticksBetweenAttempts = ticksBetweenAttempts,
                completionRange = completionRange.asSimplifiedExpression(entity),
                maxDistance = maxDistance.asSimplifiedExpression(entity),
                teleportDistance = teleportDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }
}