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
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate

/**
 * The baa's eat the graa's
 *
 * @author Hiroku
 * @since January 9th, 2025
 */
class EatGrass(
    val cooldownTicks: Int,
    val eatGrassChance: Float
) : Objective {
    var fixation: EntityFixation? = null
    val grassPredicate = BlockStatePredicate.forBlock(Blocks.GRASS_BLOCK)
    var remainingCooldownTicks = cooldownTicks

    override fun getDescription(entity: LivingEntity) = "Eat grass"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation != null) {
            return ObjectiveStatus.BLOCKED
        } else if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        val world = entity.level() as ServerLevel

        entity.pokemon.getFeature<FlagSpeciesFeature>(DataKeys.HAS_BEEN_SHEARED)?.enabled?.takeIf { it } ?: run {
            return ObjectiveStatus.INACTIVE
        }

        if (world.random.nextFloat() > eatGrassChance) {
            return ObjectiveStatus.ACTIVE
        } else {
            val blockPos = entity.blockPosition()

            // Are we not even on a grass block? Alright don't bother
            if (!grassPredicate.test(world.getBlockState(blockPos)) && !world.getBlockState(blockPos.below()).`is`(Blocks.GRASS_BLOCK)) {
                return ObjectiveStatus.ACTIVE
            }

            fixation = brain.fixate(EatGrassFixation(this))
            entity.playAnimation("eat")
            world.broadcastEntityEvent(entity, 10.toByte())
            return ObjectiveStatus.FIXATED
        }
    }

    class EatGrassFixation(val objective: EatGrass) : EntityFixation {
        var timer = 40
        override fun getDescription(entity: LivingEntity) = "Eating grass"
        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isComfort || distraction.isDamage || distraction.isForce
        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            timer--
            if (timer <= 0) {
                entity as PokemonEntity
                val world = entity.level() as ServerLevel
                val blockPos = entity.blockPosition()
                if (objective.grassPredicate.test(world.getBlockState(blockPos)) ) {
                    if (world.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING)) {
                        world.destroyBlock(blockPos, false)
                    }
                    entity.ate()
                    objective.remainingCooldownTicks = objective.cooldownTicks
                } else if (world.getBlockState(blockPos.below()).`is`(Blocks.GRASS_BLOCK)) {
                    val below = blockPos.below()
                    if (world.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING)) {
                        world.levelEvent(2001, below, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()))
                        world.setBlock(below, Blocks.DIRT.defaultBlockState(), 2)
                    }

                    entity.ate()
                    objective.remainingCooldownTicks = objective.cooldownTicks
                }
            }
            return timer > 0
        }
    }

    class Config : ObjectiveConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("400".asExpression())
        var eatGrassChance: ExpressionOrEntityVariable = Either.left("1/400".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            cooldownTicks,
            eatGrassChance
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective? {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            val eatGrassChance = eatGrassChance.resolveFloat(behaviourConfigurationContext.runtime)
            return if (eatGrassChance == 0F) {
                null
            } else {
                EatGrass(
                    cooldownTicks = cooldownTicks,
                    eatGrassChance = eatGrassChance
                )
            }
        }
    }
}