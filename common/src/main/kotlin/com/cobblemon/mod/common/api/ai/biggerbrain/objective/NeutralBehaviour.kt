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
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.AttackFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType


class NeutralBehaviour(
    speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
    engageRange: Expression = "5.0".asExpression(),
    disengageRange: Expression = "20.0".asExpression(),
    condition: Expression = "true".asExpression(),
    targetCondition: Expression = "true".asExpression(), // target condition (will be checked on entity interaction)
    description: Expression = "'Neutral Behaviour'".asExpression(),
    private val maxAttackDuration: Expression = "300".asExpression(),
    private val retaliationHitCount: Expression = "1".asExpression()
) : EntityResponseObjective(
    speedMultiplier = speedMultiplier, // How do we transfer speed to the attack behaviour? Do we want to?
    engageRange = engageRange,
    disengageRange = disengageRange,
    condition = condition, // Self/environment condition check
    targetCondition = targetCondition, // target condition (will be checked on entity interaction)
    description = description,
) {
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)
    private var fixation: EntityFixation? = null

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        if (trackedEntity != null) {
            val attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
            val avoidTarget = brain.getMemory(MemoryModuleType.AVOID_TARGET)
            if (attackTarget != null && trackedEntity?.uuid == attackTarget.uuid) {
                // Check if we continue
                return ObjectiveStatus.ACTIVE
            } else if (avoidTarget != null && trackedEntity?.uuid === avoidTarget.uuid) {
                // Check if we continue
                val runtime = mainThreadRuntime.withQueryValue("entity", (trackedEntity!!).asMostSpecificMoLangValue())
                val disengageDistance = runtime.resolveFloat(disengageRange)
                if (disengageDistance < entity.distanceTo(trackedEntity!!)) {
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
                    trackedEntity = null
                }
            } else {
                // Something has overridden us
                trackedEntity = null
            }
        }


        return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(
            priority = Priority.LOW,
            memory = MemoryModuleType.HURT_BY_ENTITY
        ) { value, labels ->
            if (value != null) {
                // Something just smacked us, see if we should get mad about it.
                val livingEntity = value
                val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
                val shouldTarget = runtime.resolveBoolean(condition)
                if (shouldTarget && brain.fixation !is AttackFixation) {
                    brain.setMemory(
                        MemoryModuleType.ATTACK_TARGET, livingEntity,
                        runtime.resolveInt(maxAttackDuration).toLong()
                    )
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
                    trackedEntity = livingEntity
                    fixation = brain.fixate(
                        AttackFixation(
                            targetEntity = trackedEntity!!,
                            speedMultiplier = speedMultiplier,
                            distractionTypes =  setOf(DistractionType.FORCE),
                            availableMoves = getRealTimeMoves(entity)
                        )
                    )
                }
            }
            return@subscribe true
        }

        brain.subscribe(
            priority = Priority.LOW,
            memory = CobblemonMemories.ATTACK_TARGET_DATA
        ) { value, labels ->
            if (value != null) {
                // We smacked them, check if it's time for tactical retreat
                val attackData = value
                val livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET) ?: return@subscribe true
                val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
                if (attackData.hitsLanded >= runtime.resolveInt(retaliationHitCount)) {
                    // Justice served, carry on
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                    brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                    brain.setMemory(MemoryModuleType.AVOID_TARGET, trackedEntity)
                }
            }
            return@subscribe true
        }
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("false".asExpression())
        val maxAttackDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val retaliationHitCount: ExpressionOrEntityVariable = Either.left("1".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("'Neutral Behaviour'".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                engageRange,
                disengageRange,
                retaliationHitCount,
                description
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return NeutralBehaviour(
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = condition.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                maxAttackDuration = maxAttackDuration.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity),
                retaliationHitCount = retaliationHitCount.asSimplifiedExpression(entity),
            )
        }
    }
}
