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
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Senses whether the entity is battling or not. Maintains the BATTLING_POKEMON memory.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class BattlingPokemonSense(
    var cooldownTicks: Int = 2
) : Sense {
    override var ticksUntilNextExecution: Int = 10
    override val memories: Set<MemoryModuleType<*>> = setOf(CobblemonMemories.BATTLING_POKEMON)

    override fun merge(other: Sense): Boolean {
        if (other is BattlingPokemonSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        val battles = when (entity) {
            is PokemonEntity -> entity.battleId?.let { BattleRegistry.getBattle(it) }?.let { listOf(it) } ?: emptyList()
            is NPCEntity -> entity.battleIds.mapNotNull(BattleRegistry::getBattle)
            else -> emptyList()
        }

        val entityUUIDs = if (entity is PokemonEntity) { // Pokemon can look at opponents, never allies
            battles.flatMap { battle ->
                val actor = battle.getActor(entity)
                if (actor != null) {
                    actor.getSide().getOppositeSide().activePokemon.mapNotNull { it.battlePokemon?.entity?.uuid }
                } else {
                    emptyList()
                }
            }
        } else if (entity is NPCEntity) { // NPCs can look at any of the pokemon in play
            battles.flatMap { it.activePokemon }.mapNotNull { it.battlePokemon?.entity?.uuid }
        } else {
            emptyList()
        }

        brain.setMemory(CobblemonMemories.BATTLING_POKEMON, entityUUIDs)
        if (entityUUIDs.isEmpty() && entity is PokemonEntity) {
            brain.eraseMemoryIfPresent(CobblemonMemories.TARGETED_BATTLE_POKEMON)
        }

        ticksUntilNextExecution = cooldownTicks
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("2".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return BattlingPokemonSense(cooldownTicks = cooldownTicks)
        }
    }
}