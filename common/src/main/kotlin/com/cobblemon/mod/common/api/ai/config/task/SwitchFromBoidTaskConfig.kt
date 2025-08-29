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
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.BoidsMoveControl
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger

class SwitchFromBoidTaskConfig : SingleTaskConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) = emptyList<MoLangConfigVariable>()
    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        behaviourConfigurationContext.addMemories(CobblemonMemories.DIAMOND_DEBUGGER)
        return BehaviorBuilder.create {
            it.group(
                it.absent(CobblemonMemories.DIAMOND_DEBUGGER)
            ).apply(it) { _ ->
                Trigger { world, entity, time ->
                    if(entity !is PokemonEntity) return@Trigger false
                    if (entity.moveControl !is PokemonMoveControl) {
                        entity.moveControl = PokemonMoveControl(entity)
                    }
                    entity.brain.updateActivityFromSchedule(world.dayTime, world.gameTime)
                    return@Trigger true
                }
            }
        }
    }
}