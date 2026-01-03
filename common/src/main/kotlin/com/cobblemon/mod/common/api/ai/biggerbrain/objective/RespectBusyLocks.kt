/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity.Companion.BATTLE_LOCK
import net.minecraft.world.entity.LivingEntity

/**
 * Objective which ensures that if the Pokémon has any busy locks, it respects them by fixating on a [BusyLockFixation]
 * which keeps them stationary.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class RespectBusyLocks : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Respect busy locks"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else {
            fixation = null

            // Maybe we should remove the busy lock for battles, unsure it's necessary since we have a special fixation for it already.
            // Hell, busy locks as a whole might not be needed with fixations being possible without an objective

            val busyLocks = if (entity is PokemonEntity) (entity.busyLocks - BATTLE_LOCK) else return ObjectiveStatus.INVALID
            if (busyLocks.isNotEmpty()) {
                if (!brain.distract(Distraction.FORCE)) {
                    return ObjectiveStatus.BLOCKED
                }
                fixation = brain.fixate(BusyLockFixation(this))
                return ObjectiveStatus.FIXATED
            } else {
                return ObjectiveStatus.INACTIVE
            }
        }
    }

    class BusyLockFixation(
        val objective: RespectBusyLocks,
    ) : EntityFixation {
        override fun getDescription(entity: LivingEntity) = "Respecting ${(entity as PokemonEntity).busyLocks.size} busy locks"

        override fun canDistract(
            distraction: Distraction,
            entity: LivingEntity
        ) = false // Might be the only fixation that can't be distracted?

        override fun tick(
            entity: LivingEntity,
            brain: BiggerBrain
        ): Boolean {
            if (entity !is PokemonEntity) {
                return false
            }
            return (entity.busyLocks - BATTLE_LOCK).isNotEmpty()
        }
    }

    class Config : ObjectiveConfig {
        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) = emptyList<MoLangConfigVariable>()
        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) = RespectBusyLocks()
    }
}