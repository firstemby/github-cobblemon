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
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
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
import net.minecraft.world.entity.ai.util.LandRandomPos

class WanderTaskConfig : SingleTaskConfig {
    companion object {
        const val WANDER = "wander" // Category
    }

    val condition = booleanVariable(WANDER, "wanders", true).asExpressible()
    val wanderChance = numberVariable(WANDER, "wander_chance", 1/(20 * 6F)).asExpressible()
    val horizontalRange = numberVariable(WANDER, "horizontal_wander_range", 10).asExpressible()
    val verticalRange = numberVariable(WANDER, "vertical_wander_range", 5).asExpressible()

    val speedMultiplier = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, SharedEntityVariables.WALK_SPEED, 0.35).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        wanderChance,
        horizontalRange,
        verticalRange,
        speedMultiplier
    ).asVariables()

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
                    if (entity !is PathfinderMob) {
                        return@Trigger false
                    }

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                    val wanderChance = wanderChance.resolveFloat()
                    if (wanderChance <= 0 || world.random.nextFloat() > wanderChance) return@Trigger false

//                    RandomStroll.stroll(speedMultiplier.resolveFloat(), horizontalRange.resolveInt(), verticalRange.resolveInt())
                    val targetVec = LandRandomPos.getPos(entity, horizontalRange.resolveInt(), verticalRange.resolveInt(), { 0.0 }) ?: return@Trigger false
                    walkTarget.set(WalkTarget(targetVec, speedMultiplier.resolveFloat(), 1))
                    lookTarget.set(BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))
                    return@Trigger true
                }
            }
        }
    }
}