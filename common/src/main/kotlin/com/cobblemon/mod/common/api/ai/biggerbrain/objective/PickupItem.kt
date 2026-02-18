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
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PickUpItemTask.dropItem
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.distanceTo
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import kotlin.math.ceil
import kotlin.math.min

/**
 * Paths to whatever item entity is marked by NEAREST_VISIBLE_WANTED_ITEM
 * Upon reaching the target attempts to place it in held item slot
 * held item slot if possible.
 * Requires NEARBY_SWEET_BERRY_BUSH memory
 *
 * @author JazzMcNade
 * @since February 8th, 2026
 */
class PickupItem(
    val ticksBetweenAttempts: Int = 60,
    val maxDistance: Expression = "14.0".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression(),
    val maxReach: Expression = "1.0".asExpression(),
) : Objective {
    var collectItemTask: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts

    override fun getDescription(entity: LivingEntity) = "Collecting item"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(collectItemTask)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        }

        val itemEntity = entity.biggerBrain.getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM) ?: return ObjectiveStatus.INACTIVE

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val maxDistance = mainThreadRuntime.resolveFloat(maxDistance)
        val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)
        val horizontalHitboxRadius = maxOf(entity.boundingBox.xsize, entity.boundingBox.zsize) / 2

        val distance = entity.distanceTo(itemEntity)
        if (distance < maxDistance) {
            if (remainingCooldownTicks <= 0 && brain.distract(idleBoredomDistraction)) {
                remainingCooldownTicks = ticksBetweenAttempts
                val movementTracker = RegularMovementTracker.attempt(
                    entity = entity,
                    walkTarget = WalkTarget(
                        itemEntity,
                        speedMultiplier,
                        min(ceil(horizontalHitboxRadius).toInt() - 1, 1),
                    )
                )

                if (movementTracker != null) {
                    val maxReach = mainThreadRuntime.resolveDouble(maxReach)
                    collectItemTask = brain.fixate(
                        fixation = PickupItemFixation(
                            entity = entity,
                            itemEntity = itemEntity,
                            maxReach = maxReach,
                            movementTracker = movementTracker
                        )
                    )
                }
            }
        }
        return ObjectiveStatus.ACTIVE
    }

    class PickupItemFixation(
        entity: PathfinderMob,
        val itemEntity: ItemEntity,
        val maxReach: Double,
        override val movementTracker: MovementTracker,
    ): EntityFixation, LookingTrackerFixation, MovementTrackerFixation {


        override val lookingTracker: LookingTracker = LookingTracker(entity = entity, lookTarget = movementTracker.walkTarget.target)
        override fun getDescription(entity: LivingEntity) = "Collecting item"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return distraction.isDamage || distraction.isForce || (movementTracker.lastStatus == MovementStatus.COMPLETED && entity.brain.getMemorySafely(
                CobblemonMemories.NEARBY_SWEET_BERRY_BUSH).orElse(null) != null)
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            lookingTracker.tick()
            movementTracker.tick()

            if (movementTracker.lastStatus == MovementStatus.COMPLETED) {
                val world = entity.level()

                if (entity !is PokemonEntity) return false

                if (!world.gameRules.getRule(GameRules.RULE_MOBGRIEFING).get()) {
                    // Not allowed to alter the world
                    return false
                }
                if (!itemEntity.isAlive || !entity.pokemon.canDropHeldItem) {
                    return false
                }

                if (entity.biggerBrain.hasMemoryValue(CobblemonMemories.IS_CONSUMING_ITEM)) return true


                if (itemEntity.distanceTo(entity) > maxReach) {
                    return false
                }

                entity.take(itemEntity, 1) // This creates the effect of the item going into the entity along with the item pickup sound
                val stack = entity.pokemon.swapHeldItem(itemEntity.item)
                if (!stack.isEmpty && !entity.level().isClientSide) {
                    dropItem(entity, stack)
                }

                return false
            } else if (movementTracker.lastStatus == MovementStatus.FAILED) {
                // Cannot reach, give up for a awhile
                entity.brain.setMemoryWithExpiry(CobblemonMemories.DISABLE_WALK_TO_WANTED_ITEM, true, 200)
                entity.brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                return false
            }

            return true
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

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return PickupItem(
                ticksBetweenAttempts = ticksBetweenAttempts,
                maxDistance = maxDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }
}