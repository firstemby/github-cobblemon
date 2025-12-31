/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.FocusDuringBattle
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.resolveBoolean
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class FocusDuringBattleConfig : ObjectiveConfig {
    var stopBattleIfHurt: ExpressionOrEntityVariable = Either.left("true".asExpression())
    var minDurationTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())
    var maxDurationTicks: ExpressionOrEntityVariable = Either.left("80".asExpression())

    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ) = listOf(
        stopBattleIfHurt,
        minDurationTicks,
        maxDurationTicks
    ).asVariables()

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
        val stopBattleIfHurt = stopBattleIfHurt.resolveBoolean(behaviourConfigurationContext.runtime)
        val minDurationTicks = minDurationTicks.resolveInt(behaviourConfigurationContext.runtime)
        val maxDurationTicks = maxDurationTicks.resolveInt(behaviourConfigurationContext.runtime)
        return FocusDuringBattle(
            stopBattleIfHurt = stopBattleIfHurt,
            minDurationTicks = minDurationTicks,
            maxDurationTicks = maxDurationTicks
        )
    }
}