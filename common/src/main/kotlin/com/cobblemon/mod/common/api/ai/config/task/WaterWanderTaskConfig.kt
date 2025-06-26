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
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.task.WanderTaskConfig.Companion.WANDER
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BehaviorUtils
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.pathfinder.PathType

class WaterWanderTaskConfig : SingleTaskConfig {
    val condition = booleanVariable(WANDER, "water_wanders", true).asExpressible()
    val wanderChance = numberVariable(WANDER, "water_wander_chance", 1/(20 * 3F)).asExpressible()
    val speedMultiplier = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, SharedEntityVariables.WALK_SPEED, 0.35).asExpressible()
    val horizontalRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
    val verticalRange: ExpressionOrEntityVariable = Either.left("3.0".asExpression())

    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return listOf(condition, wanderChance, speedMultiplier).asVariables()
    }

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean()) return null

        val wanderChanceExpression = wanderChance.asSimplifiedExpression(entity)

        return BehaviorBuilder.create {
            it.group(
                it.absent(MemoryModuleType.WALK_TARGET),
                it.registered(MemoryModuleType.LOOK_TARGET)
            ).apply(it) { walkTarget, lookTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isInWater) {
                        return@Trigger false
                    }

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                    val wanderChance = runtime.resolveFloat(wanderChanceExpression)
                    if (wanderChance <= 0 || world.random.nextFloat() > wanderChance) {
                        return@Trigger false
                    }

                    val target = BehaviorUtils.getRandomSwimmablePos(entity, horizontalRange.resolveInt(), verticalRange.resolveInt()) ?: return@Trigger false
                    walkTarget.set(
                        CobblemonWalkTarget(
                            pos = BlockPos.containing(target),
                            nodeTypeFilter = { it == PathType.WATER || it == PathType.WATER_BORDER },
                            speedModifier = speedMultiplier.resolveFloat(),
                            completionRange = 1
                        )
                    )
                    lookTarget.set(BlockPosTracker(target))
                    return@Trigger true
                }
            }
        }
    }
}