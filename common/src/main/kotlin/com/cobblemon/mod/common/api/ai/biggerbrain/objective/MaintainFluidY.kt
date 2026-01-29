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
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.OmniPathingEntity
import net.minecraft.world.entity.LivingEntity

class MaintainFluidY : Objective {
    override fun getDescription(entity: LivingEntity) = "Maintain Y in fluids"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is OmniPathingEntity) {
            return ObjectiveStatus.INVALID
        }

        val fixation = brain.fixation
        if (fixation is MovementTrackerFixation) {
            return ObjectiveStatus.BLOCKED
        }

        if (entity.isFlying()) {
            return ObjectiveStatus.INACTIVE
        }

        if (entity.canSwimInWater() && entity.isUnderWater) {
            entity.isNoGravity = true
        } else if (entity.canSwimInLava() && entity.isInLava) {
            entity.isNoGravity = true
        } else {
            entity.isNoGravity = false
        }

        return if (entity.isNoGravity) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }

    class Config : ObjectiveConfig {
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return emptyList()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return MaintainFluidY()
        }
    }
}