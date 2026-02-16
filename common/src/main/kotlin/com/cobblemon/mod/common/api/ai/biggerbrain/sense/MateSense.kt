/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.phys.AABB

class MateSense(
    private var cooldownTicks: Int = 1200
) : Sense {
    override var ticksUntilNextExecution: Int = 1200
    override val memories = setOf(
        CobblemonMemories.POTENTIAL_MATE,
        CobblemonMemories.MATE_SCAN_COOLDOWN,
        CobblemonMemories.PATERNAL_NEST_LOCATION
    )

    override fun merge(other: Sense): Boolean {
        if (other is MateSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return

        if (brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) != null) {
            return
        }

        if (brain.getMemory(CobblemonMemories.MATE_SCAN_COOLDOWN) == true) {
            return
        }

        brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)

        if (entity.pokemon.breedingCooldown != 0) return

        val world = entity.level() as? ServerLevel ?: return

        val potentialMate = world.getEntitiesOfClass(
            PokemonEntity::class.java,
            AABB(entity.blockPosition()).inflate(DETECTION_RADIUS)
        ) { nearbyEntity ->
            nearbyEntity != entity && isValidMate(entity, nearbyEntity)
        }.firstOrNull()

        brain.setMemory(CobblemonMemories.MATE_SCAN_COOLDOWN, true, MATE_SCAN_COOLDOWN)

        if (potentialMate != null) {
            brain.setMemory(CobblemonMemories.POTENTIAL_MATE, potentialMate)
            potentialMate.biggerBrain.setMemory(CobblemonMemories.POTENTIAL_MATE, entity)
            potentialMate.biggerBrain.setMemory(CobblemonMemories.MATE_SCAN_COOLDOWN, true, MATE_SCAN_COOLDOWN)
        } else {
            brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)
        }
    }

    private fun isValidMate(entity: PokemonEntity, mate: PokemonEntity): Boolean {
        if (entity.isBattling || entity.isBattleClone() || (!entity.pokemon.isWild() && entity.tethering?.tetheringId == null)) return false
        if (mate.pokemon.breedingCooldown != 0) return false

        val canBreed = BreedingLogicManager.canBreed(entity.pokemon, mate.pokemon)
        if (!canBreed) return false

        val isCompatible = BreedingLogicManager.checkCompatibility(entity.pokemon, mate.pokemon)
        val bothWild = (entity.pokemon.isWild() && mate.pokemon.isWild())
        val bothOwned = (!entity.pokemon.isWild() && !mate.pokemon.isWild())
        val mateHasMate = mate.biggerBrain.getMemory(CobblemonMemories.POTENTIAL_MATE) != null

        return !mateHasMate && isCompatible && (bothWild || bothOwned)
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("1200".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return MateSense(cooldownTicks = cooldownTicks)
        }
    }

    companion object {
        private const val DETECTION_RADIUS = 10.0
        private const val MATE_SCAN_COOLDOWN = 1200L
    }
}
