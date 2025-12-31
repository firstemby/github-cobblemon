/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities

/**
 * Senses when the Pokémon is near an adult of the same species. Maintains the NEAREST_VISIBLE_ADULT memory.
 *
 * @author broccoli
 * @since May 15th, 2024
 */
class PokemonAdultSense(
    var condition: Expression,
    var cooldownTicks: Int = 100
) : Sense {
    override var ticksUntilNextExecution: Int = 100
    override val memories = setOf(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)

    override fun merge(other: Sense): Boolean {
        if (other is PokemonAdultSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity || !brain.runtime.resolveBoolean(condition)) {
            return
        }

        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)?.let { visibleMobs ->
            setNearestPokemonAdult(entity, visibleMobs, brain)
        }
    }

    private fun setNearestPokemonAdult(entity: PokemonEntity, visibleMobs: NearestVisibleLivingEntities, brain: BiggerBrain) {
        val nearestAdult = visibleMobs.findClosest { mob ->
            targetIsValidForChild(entity, mob)
        }.map { mob -> mob as AgeableMob }.orElse(null)

        if (nearestAdult == null && brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ADULT)) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT)
        } else if (nearestAdult != null && brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT) != nearestAdult) {
            brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, nearestAdult)
        }
    }

    private fun targetIsValidForChild(child: PokemonEntity, entity: LivingEntity): Boolean {
        val potentialParent = entity as? PokemonEntity ?: return false
        val childSpecies = child.pokemon.species.resourceIdentifier

        var preEvolution = potentialParent.pokemon.preEvolution

        while (preEvolution != null) {
            if (childSpecies == preEvolution.species.resourceIdentifier) {
                return true
            }

            preEvolution = preEvolution.species.preEvolution
        }

        return false
    }

    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return PokemonAdultSense(
                condition = condition.asSimplifiedExpression(entity),
                cooldownTicks = cooldownTicks
            )
        }
    }
}