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
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.PlaceEggInNestTask
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import net.minecraft.world.entity.LivingEntity

class PlaceEggInNestTaskConfig : ObjectiveConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> = emptyList()

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective? {
        behaviourConfigurationContext.addMemories(
            CobblemonMemories.PATERNAL_NEST_LOCATION,
            CobblemonMemories.HOLDING_EGG,
            CobblemonMemories.EGG_IN_NEST
        )
        return PlaceEggInNestTask()
    }
}
