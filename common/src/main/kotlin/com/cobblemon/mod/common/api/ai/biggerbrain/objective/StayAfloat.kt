/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.booleanVariable
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.config.task.SharedEntityVariables
import com.cobblemon.mod.common.api.ai.numberVariable
import com.cobblemon.mod.common.api.ai.resolveBoolean
import com.cobblemon.mod.common.api.ai.resolveFloat
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob

class StayAfloat(
    val chance: Float = 0.8F
) : Objective {
    override fun getDescription(entity: LivingEntity) = "Stay Afloat"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PathfinderMob) {
            return ObjectiveStatus.INVALID
        }

        val fixation = brain.fixation
        if (fixation is MovementTrackerFixation && fixation.movementTracker != null) {
            // Ok if movement is in progress then we don't try staying afloat
            return ObjectiveStatus.BLOCKED
        }

        if (entity.isInWater && (entity.getFluidHeight(FluidTags.WATER) > entity.fluidJumpThreshold) ||
            (entity.isInLava && !entity.fireImmune())) {
            // Cool do something
            if (entity.random.nextFloat() < this.chance) {
                entity.jumpControl.jump()
            }
            return ObjectiveStatus.ACTIVE
        } else {
            return ObjectiveStatus.INACTIVE
        }
    }

    class Config : ObjectiveConfig {
        val condition = booleanVariable(SharedEntityVariables.MOVEMENT_CATEGORY, "can_float", true).asExpressible()
        val chance = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, "float_chance", 0.8F).asExpressible()

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            condition,
            chance
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): StayAfloat? {
            return when {
                condition.resolveBoolean(behaviourConfigurationContext.runtime) -> StayAfloat(chance.resolveFloat(behaviourConfigurationContext.runtime))
                else -> null
            }
        }
    }
}