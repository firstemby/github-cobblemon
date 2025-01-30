/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.task.WanderTaskConfig.Companion.WANDER
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos

class AirWanderTaskConfig : SingleTaskConfig {
    val condition = booleanVariable(WANDER, "air_wanders", true).asExpressible()
    val wanderChance = numberVariable(WANDER, "air_wander_chance", 1/(20 * 1F)).asExpressible()
    val horizontalRange = numberVariable(WANDER, "horizontal_wander_range", 20).asExpressible()
    val verticalRange = numberVariable(WANDER, "vertical_wander_range", 5).asExpressible()
    val speedMultiplier = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, SharedEntityVariables.WALK_SPEED, 0.35).asExpressible()


    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return listOf(condition, wanderChance, horizontalRange, verticalRange, speedMultiplier).asVariables()
    }

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean()) return null

        return BehaviorBuilder.create {
            it.group(
                it.absent(MemoryModuleType.WALK_TARGET),
                it.registered(MemoryModuleType.LOOK_TARGET)
            ).apply(it) { walkTarget, lookTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || entity.isInWater) {
                        return@Trigger false
                    }

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                    val wanderChance = wanderChance.resolveFloat()
                    if (wanderChance <= 0 || world.random.nextFloat() > wanderChance) return@Trigger false

                    val target = AirAndWaterRandomPos.getPos(entity, horizontalRange.resolveInt(), verticalRange.resolveInt(), 2, entity.lookAngle.x, entity.lookAngle.z, 2 * Math.PI)
                    if (target != null) {
                        walkTarget.set(WalkTarget(target, speedMultiplier.resolveFloat(), 3))
                        lookTarget.set(BlockPosTracker(target))
                        return@Trigger true
                    }
                    return@Trigger false
                }
            }
        }
    }
}