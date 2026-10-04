/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective


import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * Defend owner objective
 * Handles transition of nearest visible attacker to a valid attack target when wanting to defend owner.
 * Could possibly be more configurable in the future
 */
class DefendOwner : Objective {
    var cooldownTicks = 0

    override fun getDescription(entity: LivingEntity) = "Following parent species"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) return ObjectiveStatus.INVALID
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return ObjectiveStatus.INACTIVE

        val attacker = brain.getMemory(CobblemonMemories.NEAREST_VISIBLE_ATTACKER)
            ?: return ObjectiveStatus.INACTIVE

        brain.setMemory(MemoryModuleType.ATTACK_TARGET, attacker)
        return ObjectiveStatus.ACTIVE
    }

    class Config : ObjectiveConfig {
        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
            emptyList<MoLangConfigVariable>()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective? {
            if (entity !is PokemonEntity) return null
            return DefendOwner()
        }
    }
}