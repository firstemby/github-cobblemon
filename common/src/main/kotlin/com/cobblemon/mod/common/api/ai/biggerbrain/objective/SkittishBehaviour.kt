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
import com.cobblemon.mod.common.api.multiblock.condition.OrCondition
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities


class SkittishBehaviour(
    private val engageRange: Expression = "5.0".asExpression(),
    private val disengageRange: Expression = "20.0".asExpression(),
    private val condition: Expression = "true".asExpression(),
    private val targetCondition: Expression = "false".asExpression()
    ) : Objective {
    private var remainingCooldownTicks = 0
    private var trackedEntity: LivingEntity? = null
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)

    override fun getDescription(entity: LivingEntity) = "Skittish Behavior"

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
                val runtime = mainThreadRuntime.withQueryValue("entity", (trackedEntity!!).asMostSpecificMoLangValue())
                    .withQueryValue("target", (trackedEntity!!).asMostSpecificMoLangValue())
                val disengageDistance = runtime.resolveFloat(disengageRange)
                if (disengageDistance < entity.distanceTo(trackedEntity!!)
                    || !runtime.resolveBoolean(condition)
                    || !runtime.resolveBoolean(targetCondition)) {
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
                    trackedEntity = null
                }
            } else {
                // Something has overridden us
                trackedEntity = null
            }
        } else {
            // Scan visible livingEntities
            val entities: NearestVisibleLivingEntities =
                brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return ObjectiveStatus.INACTIVE

            val entity = entities.findClosest { it ->
                val runtime = mainThreadRuntime.withQueryValue("entity", (entity).asMostSpecificMoLangValue())
                    .withQueryValue("target", (it).asMostSpecificMoLangValue())
                runtime.resolveBoolean(targetCondition) && runtime.resolveFloat(engageRange) > entity.distanceTo(it)
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


    class Config : ObjectiveConfig {
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("false".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                engageRange,
                disengageRange,
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
            )
        }
    }
}
