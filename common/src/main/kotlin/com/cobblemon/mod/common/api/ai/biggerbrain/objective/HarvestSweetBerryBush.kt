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
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.distanceTo
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CaveVines
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent

/**
 * Paths to ripened sweet berry bushes AND glowberry vines with berries.
 * Upon reaching the target harvests the berries and stashes one in its
 * held item slot if possible.
 * Requires NEARBY_SWEET_BERRY_BUSH memory
 *
 * @author JazzMcNade
 * @since February 8th, 2026
 */
class HarvestSweetBerryBush(
    val ticksBetweenAttempts: Int = 60,
    val maxDistance: Expression = "14.0".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression()
) : Objective {
    var harvestTask: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts

    override fun getDescription(entity: LivingEntity) = "Harvesting sweet/glow berry"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(harvestTask)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        }

        val berryPosition = entity.biggerBrain.getMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH) ?: return ObjectiveStatus.INACTIVE

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val maxDistance = mainThreadRuntime.resolveFloat(maxDistance)
        val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)

        val distance = entity.distanceTo(berryPosition)
        if (distance < maxDistance) {
            if (remainingCooldownTicks <= 0 && brain.distract(idleBoredomDistraction)) {
                remainingCooldownTicks = ticksBetweenAttempts
                val movementTracker = RegularMovementTracker.attempt(
                    entity = entity,
                    walkTarget = WalkTarget(
                        berryPosition,
                        speedMultiplier,
                        1
                    )
                )
                if (movementTracker != null) {
                    harvestTask = brain.fixate(
                        fixation = HarvestSweetBerryBushFixation(
                            entity = entity,
                            movementTracker = movementTracker
                        )
                    )
                }
            }
        }
        return ObjectiveStatus.ACTIVE
    }


    class HarvestSweetBerryBushFixation(
        entity: PathfinderMob,
        override val movementTracker: MovementTracker,
    ): EntityFixation, LookingTrackerFixation, MovementTrackerFixation {


        override val lookingTracker: LookingTracker = LookingTracker(entity = entity, lookTarget = movementTracker.walkTarget.target)
        override fun getDescription(entity: LivingEntity) = "Harvesting sweet/glow berry"
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
                val blockPos = entity.brain.getMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH).orElse(null) ?: return false
                val blockState = world.getBlockState(blockPos)
                if (CaveVines.hasGlowBerries(blockState)) { // Glow Berries Case
                    CaveVines.use(entity, blockState, world, blockPos)
                } else if (blockState.`is`(Blocks.SWEET_BERRY_BUSH)) { // Sweet Berries Case
                    blockState.setValue(SweetBerryBushBlock.AGE, 1)
                    var j: Int = 1 + entity.level().random.nextInt(2) + if (blockState.getValue<Int>(SweetBerryBushBlock.AGE) == 3) 1 else 0
                    val itemStack: ItemStack = entity.pokemon.heldItem
                    if (itemStack.isEmpty) {
                        entity.pokemon.swapHeldItem(ItemStack(Items.SWEET_BERRIES))
                        --j
                    }
                    if (j > 0) {
                        Block.popResource(entity.level(), blockPos, ItemStack(Items.SWEET_BERRIES, j))
                    }
                    entity.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f)
                    entity.level().setBlock(blockPos, blockState.setValue(SweetBerryBushBlock.AGE, 1) as BlockState, 2)
                    entity.level().gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity))
                }
                entity.brain.eraseMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH)
                return false
            } else if (movementTracker.lastStatus == MovementStatus.FAILED) {
                // Cannot reach, give up for a awhile
                entity.brain.setMemoryWithExpiry(CobblemonMemories.DISABLE_WALK_TO_BERRY_BUSH, true, 200)
                entity.brain.eraseMemory(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH)
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
            return HarvestSweetBerryBush(
                ticksBetweenAttempts = ticksBetweenAttempts,
                maxDistance = maxDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }
}