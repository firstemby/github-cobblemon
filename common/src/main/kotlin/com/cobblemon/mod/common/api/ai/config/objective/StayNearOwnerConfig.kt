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
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.StayNearOwner
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class StayNearOwnerConfig : ObjectiveConfig {
    val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())
    val completionRange: ExpressionOrEntityVariable = Either.left("4.0".asExpression())
    val maxDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
    val teleportDistance: ExpressionOrEntityVariable = Either.left("24.0".asExpression())
    val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ) = listOf(
        ticksBetweenAttempts,
        completionRange,
        maxDistance,
        teleportDistance,
        speedMultiplier
    ).asVariables()

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
        val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
        return StayNearOwner(
            ticksBetweenAttempts = ticksBetweenAttempts,
            completionRange = completionRange.asSimplifiedExpression(entity),
            maxDistance = maxDistance.asSimplifiedExpression(entity),
            teleportDistance = teleportDistance.asSimplifiedExpression(entity),
            speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
        )
    }
}