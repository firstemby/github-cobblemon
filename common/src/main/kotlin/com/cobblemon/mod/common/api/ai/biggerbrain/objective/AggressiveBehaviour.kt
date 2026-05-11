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
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities


class AggressiveBehaviour(
    private val speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
    private val engageRange: Expression = "15.0".asExpression(),
    private val disengageRange: Expression = "20.0".asExpression(),
    private val condition: Expression = "true".asExpression(), // Self/environment condition check
    private val targetCondition: Expression = "true".asExpression(), // target condition (will be checked on entity interaction)
    private val maxAttackDuration: Expression = "300".asExpression(),
    description: Expression = "Aggressive Behaviour".asExpression(),

    ) : Objective {
    private var remainingCooldownTicks = 0
    private var trackedEntity: LivingEntity? = null
    private val objectiveDescription : String = mainThreadRuntime.resolveString(description)
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)

    override fun getDescription(entity: LivingEntity) = objectiveDescription

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID


        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        } else {
            remainingCooldownTicks = 20
        }


        val attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
        if (trackedEntity != null) {
            if (attackTarget != null && trackedEntity?.uuid == attackTarget.uuid) {
                // Check if we continue
                val runtime = mainThreadRuntime.withQueryValue("entity", (trackedEntity!!).asMostSpecificMoLangValue())
                if (!runtime.resolveBoolean(condition) // self condition
                    || !runtime.resolveBoolean(targetCondition) // target condition
                    || !(runtime.resolveFloat(disengageRange) > entity.distanceTo(trackedEntity!!))) {
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                    brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                    return ObjectiveStatus.INACTIVE
                }
                return ObjectiveStatus.ACTIVE
            } else {
                // Something has overridden us and independently set the attack target
                trackedEntity = null
                return ObjectiveStatus.BLOCKED
            }
        } else {
            // Check if we're in the mood to fight
            if (mainThreadRuntime.withQueryValue("entity", (entity).asMostSpecificMoLangValue()).resolveBoolean(condition)) {
                // Scan visible livingEntities
                val entities: NearestVisibleLivingEntities =
                    brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return ObjectiveStatus.INACTIVE
                var attackDuration = 20
                val targetEntity = entities.findClosest { it ->
                    val runtime = mainThreadRuntime.withQueryValue("target", (it).asMostSpecificMoLangValue())
                    val isValidTarget = runtime.resolveBoolean(targetCondition) && runtime.resolveFloat(engageRange) > entity.distanceTo(it)
                    if (isValidTarget) {
                        attackDuration = runtime.resolveInt(maxAttackDuration)
                    }
                    isValidTarget

                }.orElse(null)
                if (targetEntity != null) {
                    trackedEntity = targetEntity
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, targetEntity, attackDuration.toLong())
                }
            } else {
                ObjectiveStatus.INACTIVE
            }

        }
        return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }

    override fun <T> onMemoryChanged(
        entity: LivingEntity,
        brain: BiggerBrain,
        memory: MemoryModuleType<T>,
        value: T?
    ) {
        if (memory == CobblemonMemories.ATTACK_TARGET_DATA && value != null) {

            val livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET) ?: return
            if (trackedEntity?.uuid == livingEntity.uuid) {
                // We smacked them, refresh our attack timer
                val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
                val attackDuration = runtime.resolveInt(maxAttackDuration)
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity, attackDuration.toLong())
            }
        }
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val engageRange: ExpressionOrEntityVariable = Either.left("15.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("20.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("false".asExpression())
        val maxAttackDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("Aggressive Behaviour".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                speedMultiplier,
                engageRange,
                disengageRange,
                maxAttackDuration,
                description
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return AggressiveBehaviour(
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = targetCondition.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                maxAttackDuration = maxAttackDuration.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity)
            )
        }
    }
}
