/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class AddObjectivesConfig : BehaviourConfig {
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val objectivesByPriority: Map<Int, List<ObjectiveConfig>> = mapOf()

    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): List<MoLangConfigVariable> {
       return listOf(condition).asVariables() + objectivesByPriority
           .values
           .flatten()
           .flatMap { it.getVariables(entity, behaviourConfigurationContext) }
    }

    override fun configure(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext){
        if (!checkCondition(behaviourConfigurationContext, condition)) return

        for ((priority, objectiveConfigs) in objectivesByPriority) {
            val objectives = objectiveConfigs.mapNotNull { it.create(entity, behaviourConfigurationContext) }
            val priorityObjectives = behaviourConfigurationContext.objectives.getOrPut(priority) { mutableListOf() }
            priorityObjectives.addAll(objectives)
        }
    }
}