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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class FindHerdLeaderTaskConfig : SingleTaskConfig {
    // How frequently to check for whether it should herd. Probably isn't that expensive but might use this for chance.
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
        val checkTicksValue = checkTicks.resolveInt()
        return BehaviorBuilder.create { instance ->
            instance.group(
                instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                instance.absent(CobblemonMemories.HERD_LEADER),
                instance.absent(MemoryModuleType.WALK_TARGET)
            ).apply(instance) { nearbyEntities, herdLeader, _ ->
                Trigger { world, entity, _ ->
                    if (world.gameTime % checkTicksValue != 0L) {
                        return@Trigger false
                    }

                    entity as PokemonEntity
                    val toleratedLeaders = entity.behaviour.herd.toleratedLeaders
                    val nearbyLeaders = instance.get(nearbyEntities).findAll {
                        it is PokemonEntity &&
                                it.pokemon.storeCoordinates.get()?.store !is PartyStore &&
                                it.exposedSpecies.resourceIdentifier in toleratedLeaders &&
                                !it.brain.hasMemoryValue(CobblemonMemories.HERD_LEADER) &&
                                it.pokemon.level >= entity.pokemon.level
                    }.toList()

                    if (nearbyLeaders.isEmpty()) {
                        return@Trigger false
                    }

                    val leader = nearbyLeaders
                        .filterIsInstance<PokemonEntity>()
                        .filter { it.getHerdSize() < entity.behaviour.herd.maxSize }
                        .maxByOrNull { it.pokemon.level }
                        ?: return@Trigger false

                    herdLeader.set(leader.uuid.toString())
                    return@Trigger true
                }
            }
        }
    }
}