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
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.LookAtNearbyEntities
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

class LookAtNearbyEntitiesConfig : ObjectiveConfig {
    val maxDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
    val minDuration: ExpressionOrEntityVariable = Either.left("20".asExpression())
    val maxDuration: ExpressionOrEntityVariable = Either.left("80".asExpression())

    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ) = listOf(
        maxDistance,
        minDuration,
        maxDuration
    ).asVariables()

    override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
        val maxDistance = maxDistance.resolveDouble(behaviourConfigurationContext.runtime)
        val minDuration = minDuration.resolveInt(behaviourConfigurationContext.runtime)
        val maxDuration = maxDuration.resolveInt(behaviourConfigurationContext.runtime)
        return LookAtNearbyEntities(
            maxDistance = maxDistance,
            minDuration = minDuration,
            maxDuration = maxDuration
        )
    }
}