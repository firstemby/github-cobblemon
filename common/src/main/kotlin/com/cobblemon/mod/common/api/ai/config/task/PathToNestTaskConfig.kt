/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.resolveBoolean
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.PathToNestTask
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class PathToNestTaskConfig : ObjectiveConfig {
    companion object {
        const val BREED = "breed"
    }

    private val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    private val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.6".asExpression())

    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> {
        return listOf(condition, speedMultiplier).asVariables()
    }

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective? {
        if (!condition.resolveBoolean(behaviourConfigurationContext.runtime)) return null
        behaviourConfigurationContext.addMemories(
            CobblemonMemories.POTENTIAL_MATE,
            CobblemonMemories.NEST_LOCATION,
            CobblemonMemories.PATERNAL_NEST_LOCATION,
            CobblemonMemories.EGG_IN_NEST,
            CobblemonMemories.EGG_LOCATION,
            CobblemonMemories.HOLDING_EGG
        )
        val speedExpression: Expression = speedMultiplier.asSimplifiedExpression(entity)
        return PathToNestTask(speedExpression)
    }
}
