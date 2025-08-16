/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import net.minecraft.core.BlockPos
import net.minecraft.util.valueproviders.UniformInt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import kotlin.math.max

object MoveToSweetBerryBushTask {
    fun create(
        executionRange: UniformInt,
        speed: Float
    ): OneShot<LivingEntity> = BehaviorBuilder.create { context ->
        context.group(
            context.present(CobblemonMemories.NEARBY_SWEET_BERRY_BUSH),
            context.registered(MemoryModuleType.LOOK_TARGET),
            context.absent(MemoryModuleType.WALK_TARGET)
        ).apply(context) { nearestSweetBerryBush, lookTarget, walkTarget ->
            Trigger { _, entity, _ ->
                val blockPos = context.get(nearestSweetBerryBush) as BlockPos
                if (
                        blockPos.closerToCenterThan(entity.position(), (executionRange.maxValue + 1).toDouble())
                        && !blockPos.closerToCenterThan(entity.position(), (executionRange.minValue).toDouble())
                ) {
                    val walkTargetX = WalkTarget(blockPos, speed, max(executionRange.minValue - 1, 0))
                    lookTarget.set(BlockPosTracker(blockPos))
                    walkTarget.set(walkTargetX)
                    true
                } else {
                    false
                }
            }
        }
    }
}
