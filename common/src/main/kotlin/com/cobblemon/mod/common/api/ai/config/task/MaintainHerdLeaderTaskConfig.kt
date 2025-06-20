/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import java.util.UUID
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Task that looks around for a better herd leader or gives up on a leader
 * that is themselves following a different Pokémon.
 *
 * @author Hiroku
 * @since June 16th, 2025
 */
class MaintainHerdLeaderTaskConfig : SingleTaskConfig {
    // How frequently to check for a better herd leader
    val checkTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())

    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return listOf(checkTicks).asVariables()
    }

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) {
            return null
        }
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val checkTicks = checkTicks.resolveInt()
        return BehaviorBuilder.create { instance ->
            instance.group(
                instance.present(CobblemonMemories.HERD_LEADER),
                instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            ).apply(instance) { herdLeader, nearbyEntities ->
                Trigger { world, entity, _ ->
                    entity as PokemonEntity
                    if (world.gameTime % checkTicks != 0L) {
                        return@Trigger false
                    }

                    val leader = instance.get(herdLeader).let(UUID::fromString).let(world::getEntity) as? PokemonEntity
                    if (leader == null) {
                        entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER)
                        return@Trigger true // No leader, so we erase the memory
                    } else if (leader.brain.hasMemoryValue(CobblemonMemories.HERD_LEADER)) {
                        entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER)
                        return@Trigger true
                    } else if (leader.exposedSpecies.resourceIdentifier !in entity.behaviour.herd.toleratedLeaders) {
                        // Zoroark getting exposed then herd members pausing and saying "hey wait a minute"
                        entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER)
                    } else {
                        val bestSurroundingLeader = instance.get(nearbyEntities).findAll {
                            it is PokemonEntity &&
                                    it != leader &&
                                    it.pokemon.storeCoordinates.get()?.store !is PartyStore &&
                                    it.exposedSpecies.resourceIdentifier in entity.behaviour.herd.toleratedLeaders &&
                                    !it.brain.hasMemoryValue(CobblemonMemories.HERD_LEADER) &&
                                    it.pokemon.level > leader.pokemon.level // Has to be a superior leader to the existing one
                        }.maxByOrNull { (it as PokemonEntity).pokemon.level } as? PokemonEntity
                        if (bestSurroundingLeader != null) {
                            entity.brain.setMemory(CobblemonMemories.HERD_LEADER, bestSurroundingLeader.uuid.toString())
                        }
                    }
                    return@Trigger true
                }
            }
        }
    }

}