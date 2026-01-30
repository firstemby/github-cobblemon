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
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.AttackEggThiefTask
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class AttackEggThiefTaskConfig : ObjectiveConfig {
    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> = emptyList()

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective? {
        behaviourConfigurationContext.addMemories(
            CobblemonMemories.EGG_THIEF,
            CobblemonMemories.EGG_SEARCH_COUNTDOWN,
            MemoryModuleType.ANGRY_AT,
            MemoryModuleType.ATTACK_TARGET
        )
        return AttackEggThiefTask()
    }
}
