/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.task.SharedEntityVariables.FLEE_DESIRED_DISTANCE
import com.cobblemon.mod.common.api.ai.config.task.SharedEntityVariables.FLEE_SPEED_MULTIPLIER
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.SensorType

class FleeAttackerTaskConfig : SingleTaskConfig {
    var condition = booleanVariable(SharedEntityVariables.FEAR_CATEGORY, "flee_attacker", true).asExpressible()
    var speedMultiplier = numberVariable(SharedEntityVariables.FEAR_CATEGORY, FLEE_SPEED_MULTIPLIER, 0.5).asExpressible()
    var desiredDistance = numberVariable(SharedEntityVariables.FEAR_CATEGORY, FLEE_DESIRED_DISTANCE, 9).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        speedMultiplier,
        desiredDistance
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PathfinderMob) return null

        behaviourConfigurationContext.addMemories(MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.WALK_TARGET)
        behaviourConfigurationContext.addSensors(SensorType.HURT_BY)

        val speedMultiplier = speedMultiplier.resolveFloat()
        val desiredDistance = desiredDistance.resolveInt()
        return WrapperLivingEntityTask(
            SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, speedMultiplier, desiredDistance, false),
            PathfinderMob::class.java
        )
    }
}