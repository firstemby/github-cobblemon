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
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.MoveToOwnerTask
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class MoveToOwnerTaskConfig : SingleTaskConfig {
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    // Gap between the pokemon's hitbox and the owner's where it stops
    var completionRange: ExpressionOrEntityVariable = Either.left("2.0".asExpression())
    var speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())
    var teleportDistance: ExpressionOrEntityVariable = Either.left("24.0".asExpression())
    var maxDistance: ExpressionOrEntityVariable = Either.left("14.0".asExpression())
    // Gap between the pokemon's hitbox and the owner's before it sprints to catch up. Past maxDistance so short
    // catch ups are walked
    var sprintDistance: ExpressionOrEntityVariable = Either.left("14.0".asExpression())

    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) = listOf(
        condition,
        completionRange,
        speedMultiplier,
        teleportDistance,
        maxDistance,
        sprintDistance
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity> {
        behaviourConfigurationContext.addMemories(
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET
        )
        return WrapperLivingEntityTask(
            MoveToOwnerTask.create(
                condition = condition.asExpression(),
                completionRange = completionRange.asExpression(),
                speedMultiplier = speedMultiplier.asExpression(),
                teleportDistance = teleportDistance.asExpression(),
                maxDistance = maxDistance.asExpression(),
                sprintDistance = sprintDistance.asExpression()
            ),
            PokemonEntity::class.java
        )
    }
}