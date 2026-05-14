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
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class SkittishBehaviour(
    engageRange: Expression = "5.0".asExpression(),
    disengageRange: Expression = "20.0".asExpression(),
    condition: Expression = "true".asExpression(),
    targetCondition: Expression = "false".asExpression(),
    description: Expression = "'Skittish Behaviour'".asExpression(),
    ) : EntityResponseObjective(
//    speedMultiplier = speedMultiplier, // How do we transfer speed to the attack behaviour? Do we want to?
    engageRange = engageRange,
    disengageRange = disengageRange,
    condition = condition, // Self/environment condition check
    targetCondition = targetCondition, // target condition (will be checked on entity interaction)
    description = description,
) {
    private var remainingCooldownTicks = 0
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val avoidTarget = brain.getMemory(MemoryModuleType.AVOID_TARGET)

        if (trackedEntity != null) {
            if (avoidTarget != null && trackedEntity?.uuid === avoidTarget.uuid) {
                // Check if we continue
                if(!shouldContinue(entity, brain)) {
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
                    trackedEntity = null
                }
            } else {
                // Something has overridden us
                trackedEntity = null
            }
        } else {
            // Scan visible livingEntities
            val targetedEntity = scanForValidTargets(entity, brain)
            if (targetedEntity != null) {
                trackedEntity = targetedEntity
                brain.setMemory(MemoryModuleType.AVOID_TARGET, trackedEntity)
            }
        }


        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }

        return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }


    class Config : ObjectiveConfig {
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("false".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("'Skittish Behaviour'".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                engageRange,
                disengageRange,
                description,
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return SkittishBehaviour(
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = targetCondition.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity)
            )
        }
    }
}
