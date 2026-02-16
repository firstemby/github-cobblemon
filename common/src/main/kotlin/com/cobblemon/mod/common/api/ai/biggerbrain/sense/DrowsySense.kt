/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Senses when the Pokémon is in a situation that would make it ready to sleep.
 * Either by choice (nighttime) or by force (battle/spawning with sleep status)
 *
 * @author Hiroku
 * @since March 23rd, 2024
 */
class DrowsySense(
    var cooldownTicks: Int = 20
) : Sense {
    override var ticksUntilNextExecution: Int = 20
    override val memories = setOf(CobblemonMemories.POKEMON_DROWSY)

    override fun merge(other: Sense): Boolean {
        if (other is DrowsySense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        if (entity !is PokemonEntity) {
            return
        }

        val world = entity.level()
        val rest = entity.behaviour.resting
        val isDrowsy = brain.getMemory(CobblemonMemories.POKEMON_DROWSY) ?: false
        val hurtLately = brain.hasMemoryValue(MemoryModuleType.HURT_BY)
        val shouldBeDrowsy = rest.canSleep && !hurtLately && (world.dayTime.toInt() % 24000) in rest.times && !brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
        val forcedAsleep = entity.pokemon.status?.status == Statuses.SLEEP

        if (entity.isBattling) {
            if (!isDrowsy && forcedAsleep) {
                brain.setMemory(CobblemonMemories.POKEMON_DROWSY, true)
            } else if (isDrowsy && !forcedAsleep) {
                brain.eraseMemory(CobblemonMemories.POKEMON_DROWSY)
            }
        } else {
            if (!isDrowsy && shouldBeDrowsy && entity.random.nextFloat() < rest.drowsyChance) {
                brain.setMemory(CobblemonMemories.POKEMON_DROWSY, true)
            } else if (isDrowsy && !shouldBeDrowsy) {
                brain.eraseMemory(CobblemonMemories.POKEMON_DROWSY)
            } else if (isDrowsy && entity.random.nextFloat() < rest.rouseChance) {
                brain.eraseMemory(CobblemonMemories.POKEMON_DROWSY)
            }
        }

        ticksUntilNextExecution = cooldownTicks
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("20".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return DrowsySense(cooldownTicks = cooldownTicks)
        }
    }
}