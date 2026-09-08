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
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
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
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities


class Defensive(
    private val speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
    private val engageRange: Expression = "5.0".asExpression(),
    private val disengageRange: Expression = "20.0".asExpression(),
    private val condition: Expression = "true".asExpression(),
    private val maxAttackDuration: Expression = "300".asExpression(),
    private val retaliationHitCount: Expression = "1".asExpression()
) : Objective {
    private var remainingCooldownTicks = 0
    private var trackedEntity: LivingEntity? = null
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)

    override fun getDescription(entity: LivingEntity) = "Defensive Behavior"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
        val avoidTarget = brain.getMemory(MemoryModuleType.AVOID_TARGET)

        if (trackedEntity != null) {
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
                // Something has overrided us
                trackedEntity = null
            }
        } else {
            // Scan visible livingEntities
            val entities: NearestVisibleLivingEntities =
                brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return ObjectiveStatus.INACTIVE

            val entity = entities.findClosest { it ->
                val runtime = mainThreadRuntime.withQueryValue("entity", (it).asMostSpecificMoLangValue())
                runtime.resolveBoolean(condition) && runtime.resolveFloat(engageRange) > entity.distanceTo(it)
            }.orElse(null)
            if (entity != null) {
                trackedEntity = entity
                brain.setMemory(MemoryModuleType.AVOID_TARGET, entity)
            }
        }


        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }

        return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(
            priority = Priority.LOW,
            memory = MemoryModuleType.HURT_BY_ENTITY
        ) { value, labels -> onHurtByEntityChanged(brain, value, labels) }

        brain.subscribe(
            priority = Priority.LOW,
            memory = CobblemonMemories.ATTACK_TARGET_DATA
        ) { value, labels -> onAttackTargetDataChanged(brain, value, labels) }
    }

    fun onHurtByEntityChanged(brain: BiggerBrain, livingEntity: LivingEntity?, labels: MutableSet<String>): Boolean {
        if (livingEntity != null) {
            // Something just smacked us, see if we should get mad about it.
            val livingEntity = livingEntity as LivingEntity
            val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
            val shouldTarget = runtime.resolveBoolean(condition)
            if (shouldTarget) {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity,
                    runtime.resolveInt(maxAttackDuration).toLong()
                )
                brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
                trackedEntity = livingEntity
            }
        }
        return true
    }

    fun onAttackTargetDataChanged(brain: BiggerBrain, attackData: CobblemonAttackTargetData?, labels: MutableSet<String>): Boolean {
        if (attackData != null) {
            // We smacked them, check if it's time for tactical retreat
            val livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET) ?: return true
            val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
            if (attackData.hitsLanded >= runtime.resolveInt(retaliationHitCount)) {
                // Justice served, carry on
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                brain.setMemory(MemoryModuleType.AVOID_TARGET, trackedEntity)
            }
        }
        return true // Other listeners can handle it that's fine
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val maxAttackDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val retaliationHitCount: ExpressionOrEntityVariable = Either.left("1".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(condition, speedMultiplier, engageRange, disengageRange, maxAttackDuration, retaliationHitCount).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return Defensive(
                condition = condition.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                maxAttackDuration = maxAttackDuration.asSimplifiedExpression(entity),
                retaliationHitCount = retaliationHitCount.asSimplifiedExpression(entity),
                )
        }
    }
}
