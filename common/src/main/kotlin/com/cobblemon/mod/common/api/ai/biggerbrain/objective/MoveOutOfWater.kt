/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.*
import com.cobblemon.mod.common.api.ai.biggerbrain.*
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.util.*
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.entity.ai.util.RandomPos
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.Vec3


/**
 * Tries to path out of water if the entity usually water to avoid water.
 * Will pathfind through water even if the entity usually filters out water path nodes.
 * If land cannot be found in the entity's wander range it'll wander randomly in a spiral-ish pattern
 *
 * @author JazzMcNade
 * @since January 17th, 2026
 */
class MoveOutOfWater(
    val ticksBetweenAttempts: Int = 60,
    val maxDistance: Expression = "14.0".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression()
) : Objective {
    var movementTask: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts
    var wanderDirection: Vec3? = null
    var isWanderTurnDirectionClockwise = true
    var wanderTurnChance = BASE_TURN_CHANCE
    var failCount = 0
    override fun getDescription(entity: LivingEntity) = "Move out of water"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is TamableAnimal) {
            return ObjectiveStatus.INVALID
        }

        if (brain.isFixation(movementTask)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        }
        if (!entity.isInWater || entity.isUnderWater) {
            // Focus on not drowning first
            // TODO: 100% boned if in an underwater cave
            return ObjectiveStatus.INACTIVE
        }


        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        } else {
            remainingCooldownTicks = ticksBetweenAttempts
        }

        val maxDistance = mainThreadRuntime.resolveInt(maxDistance)
        val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)

        val runtime = mainThreadRuntime
        val wanderControl = entity.brain.getMemorySafely(CobblemonMemories.WANDER_CONTROL).orElse(null)
        val allowLand = wanderControl?.allowLand
            ?: runtime.resolveBoolean("(q.entity.movement.can_walk && !q.entity.movement.avoids_land) || q.entity.movement.can_fly".asExpression())
        val allowAir = wanderControl?.allowAir
            ?: runtime.resolveBoolean("q.entity.movement.can_fly && !q.entity.movement.can_walk".asExpression())

        val types = mutableSetOf<WanderType>()
        if ((allowLand || allowAir)) {
            types.add(WanderType.LAND)
        }


        if (types.isEmpty()) {
            return ObjectiveStatus.INACTIVE
        }

        if (brain.distract(comfortDistraction)) {
            var walkTarget: CobblemonWalkTarget? = null
            val targetVec3 = LandRandomPos.getPos(entity, maxDistance, maxDistance)
            if (targetVec3 != null) {
                walkTarget = CobblemonWalkTarget(
                    BlockPos.containing(targetVec3.x, targetVec3.y, targetVec3.z),
                    speedMultiplier,
                    0,
                    nodeTypeFilter = { nodeType ->
                        true
                    }
                )
            }

            if (walkTarget == null) {
                if (failCount >= 5) {
                    // No out of water target black with a malus of 0 could be found.
                    // Need to choose a direction and move as far as possible, hoping we randomly find land
                    // Totally possible that a shoreline is *just* outside of the entity's wander range and
                    // we choose a direction that's opposite of the shoreline
                    // To counteract this, the pokemon moves in a (very roughly) spiral pattern outward from the origin
                    var wanderPosition: BlockPos? = null
                    if (wanderDirection == null) {
                        val wanderOffset = RandomPos.generateRandomDirection(entity.level().random, maxDistance / 2, 0)
                        wanderDirection = wanderOffset.toVec3d().normalize()
                        isWanderTurnDirectionClockwise = entity.random.nextInt(2) > 0
                        wanderTurnChance = BASE_TURN_CHANCE
                        wanderPosition = entity.blockPosition().offset(wanderOffset.x, wanderOffset.y, wanderOffset.z)
                    } else {

                        if (entity.level().random.nextFloat() < wanderTurnChance) {
                            // Turn 30 degrees
                            wanderDirection =
                                wanderDirection!!.yRot(Math.PI.toFloat() / 3.0f * if (isWanderTurnDirectionClockwise) -1f else 1f)
                            wanderTurnChance *= 0.7
                        }
                        val wanderVec = entity.position().add(wanderDirection!!.scale(maxDistance.toDouble()))
                        wanderPosition = BlockPos.containing(wanderVec)

                    }
                    val wanderTarget = generateWaterSafePosToward(entity, maxDistance, wanderPosition)

                    if (wanderTarget != null) {
                        walkTarget = CobblemonWalkTarget(
                            wanderPosition,
                            speedMultiplier,
                            0,
                            nodeTypeFilter = { nodeType ->
                                true
                            }
                        )
                    } else {
                        return ObjectiveStatus.ACTIVE
                    }
                } else {
                    failCount++
                }

            }
            if (walkTarget != null) {
                failCount = 0
                val movementTracker = RegularMovementTracker.attempt(entity, walkTarget)
                    ?: return ObjectiveStatus.ACTIVE
                movementTask = brain.fixate(
                    fixation = StayOutOfWaterFixation(
                        movementTracker = movementTracker
                    )
                )
            }
        }
        return ObjectiveStatus.ACTIVE
    }

    fun generateWaterSafePosToward(
        mob: PathfinderMob,
        range: Int,
        targetPos: BlockPos
    ): BlockPos? {

        for (i in 0..4) {
            val candidate: BlockPos? = RandomPos.generateRandomPosTowardDirection(
                mob,
                range,
                mob.level().random,
                targetPos
            )

            if (candidate == null) continue

            val pathType = mob.navigation.nodeEvaluator.getPathType(mob, candidate)
            // Don't actually care if it can be reached, only if it's not hazardous
            if (pathType == PathType.WATER || mob.getPathfindingMalus(pathType) <= 0)
                return candidate

        }
        return null
    }

    companion object {
        const val BASE_TURN_CHANCE = 0.8
    }

    class StayOutOfWaterFixation(
        override val movementTracker: MovementTracker,
    ) : EntityFixation, MovementTrackerFixation {
        var ticksOutOfWater = 0
        override fun getDescription(entity: LivingEntity) = "Moving out of water"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            // Easily distracted once we are no longer touching water.
            return distraction.isDamage || distraction.isForce || movementTracker.lastStatus == MovementStatus.COMPLETED
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            val movementStatus = tickMovementFixation()
            if (movementStatus == MovementStatus.COMPLETED) return false
            if (entity.isInWater) {
                ticksOutOfWater = 0
            } else {
                ticksOutOfWater++
                if (ticksOutOfWater > 20) {
                    // Made it out of water, job done
                    return false
                }
            }
            return movementStatus != MovementStatus.FAILED
        }
    }

    class Config : ObjectiveConfig {
        val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val maxDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            ticksBetweenAttempts,
            maxDistance,
            speedMultiplier
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return MoveOutOfWater(
                ticksBetweenAttempts = ticksBetweenAttempts,
                maxDistance = maxDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }
}