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
import net.minecraft.world.entity.ai.behavior.RandomStroll
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget

class WaterWanderTaskConfig : SingleTaskConfig {
    val condition = booleanVariable(WANDER, "water_wanders", true).asExpressible()
    val wanderChance = numberVariable(WANDER, "water_wander_chance", 1/(20 * 3F)).asExpressible()
    val speedMultiplier = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, SharedEntityVariables.WALK_SPEED, 0.35).asExpressible()


    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return listOf(condition, wanderChance, speedMultiplier).asVariables()
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
                    if (entity !is PathfinderMob || !entity.isUnderWater) {
                        return@Trigger false
                    }

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                    val wanderChance = wanderChance.resolveFloat()
                    if (wanderChance <= 0 || world.random.nextFloat() > wanderChance) return@Trigger false

                    val target = RandomStroll.getTargetSwimPos(entity)
                    if (target != null) {
                        walkTarget.set(WalkTarget(target, speedMultiplier.resolveFloat(), 1))
                        lookTarget.set(BlockPosTracker(target))
                        return@Trigger true
                    }
                    return@Trigger false
                }
            }
        }
    }
}