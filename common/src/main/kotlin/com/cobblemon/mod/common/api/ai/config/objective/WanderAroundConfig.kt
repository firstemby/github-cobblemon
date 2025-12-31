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
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.WanderAround
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class WanderAroundConfig : ObjectiveConfig {
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val cooldownTicks: ExpressionOrEntityVariable = Either.left("80".asExpression())
    val allowLand: ExpressionOrEntityVariable = Either.left("(q.entity.behaviour.moving.walk.can_walk && !q.entity.behaviour.moving.walk.avoids_land) || q.entity.behaviour.moving.fly.can_fly".asExpression())
    val allowWater: ExpressionOrEntityVariable = Either.left("q.entity.behaviour.moving.swim.can_swim_in_water && !q.entity.behaviour.moving.swim.avoids_water".asExpression())
    val allowAir: ExpressionOrEntityVariable = Either.left("q.entity.behaviour.moving.fly.can_fly && !q.entity.behaviour.moving.walk.can_walk".asExpression())
    val wanderChance: ExpressionOrEntityVariable = Either.left("1/(20*6)".asExpression())
    val horizontalRange: ExpressionOrEntityVariable = Either.left("10".asExpression())
    val verticalRange: ExpressionOrEntityVariable = Either.left("5".asExpression())
    val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.35".asExpression())
    val avoidTargetingAir: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val minimumHeight: ExpressionOrEntityVariable = Either.left("0".asExpression())
    val maximumHeight: ExpressionOrEntityVariable = Either.left("999".asExpression())

    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): List<MoLangConfigVariable> {
        return listOf(
            condition,
            cooldownTicks,
            allowLand,
            allowWater,
            allowAir,
            wanderChance,
            horizontalRange,
            verticalRange,
            speedMultiplier,
            avoidTargetingAir,
            minimumHeight,
            maximumHeight
        ).asVariables()
    }

    override fun create(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): Objective {
        return WanderAround(
            condition = condition.asSimplifiedExpression(entity),
            cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime),
            allowLand = allowLand.asSimplifiedExpression(entity),
            allowWater = allowWater.asSimplifiedExpression(entity),
            allowAir = allowAir.asSimplifiedExpression(entity),
            wanderChance = wanderChance.asSimplifiedExpression(entity),
            horizontalRange = horizontalRange.asSimplifiedExpression(entity),
            verticalRange = verticalRange.asSimplifiedExpression(entity),
            speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
            avoidTargetingAir = avoidTargetingAir.asSimplifiedExpression(entity),
            minimumHeight = minimumHeight.asSimplifiedExpression(entity),
            maximumHeight = maximumHeight.asSimplifiedExpression(entity)
        )
    }
}