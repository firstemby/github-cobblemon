/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask.Companion.wrapped
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.task.WanderTaskConfig.Companion.WANDER
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.ai.CircleAroundTask
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

class FlyInCirclesTaskConfig : SingleTaskConfig {
    val poseTypes: Set<PoseType> = setOf(
        PoseType.FLY,
        PoseType.HOVER
    )

    val minAngularVelocityDegrees = numberVariable(WANDER, "min_fly_circling_angular_velocity", 0.0).asExpressible()
    val maxAngularVelocityDegrees = numberVariable(WANDER, "max_fly_circling_angular_velocity", 2.0).asExpressible()
    val speed = numberVariable(WANDER, "fly_circling_speed", 0.6).asExpressible()
    val verticalSpeed: ExpressionOrEntityVariable = Either.left("0.0".asExpression())
    val minDurationTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())
    val maxDurationTicks: ExpressionOrEntityVariable = Either.left("180".asExpression())

    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return listOf(
            minAngularVelocityDegrees,
            maxAngularVelocityDegrees,
            speed,
            verticalSpeed,
            minDurationTicks,
            maxDurationTicks
        ).asVariables()
    }

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) {
            return null
        }
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        return CircleAroundTask(
            poseTypes = poseTypes,
            minTurnAngleDegrees = minAngularVelocityDegrees.resolveFloat(),
            maxTurnAngleDegrees = maxAngularVelocityDegrees.resolveFloat(),
            speed = speed.resolveFloat(),
            verticalSpeed = verticalSpeed.resolveFloat(),
            minDurationTicks = minDurationTicks.resolveFloat().toInt(),
            maxDurationTicks = maxDurationTicks.resolveFloat().toInt()
        ).wrapped<PokemonEntity>()
    }
}