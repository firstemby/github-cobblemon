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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

object PathToBeeHiveTask {

    fun create(): OneShot<in LivingEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.registered(MemoryModuleType.LOOK_TARGET),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.POLLINATED),
                it.present(CobblemonMemories.HIVE_LOCATION),
                it.absent(CobblemonMemories.HIVE_COOLDOWN)
            ).apply(it) { lookTarget, walkTarget, pollinated, hiveMemory, hiveCooldown ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) {
                        return@Trigger false
                    }

                    val hiveLocation = it.get(hiveMemory)
                    val sidesByClosest = listOf(
                        hiveLocation.north(),
                        hiveLocation.south(),
                        hiveLocation.east(),
                        hiveLocation.west()
                    ).sortedBy { it.distSqr(entity.blockPosition()) }

                    var openSide = BlockPos.ZERO
                    for (side in sidesByClosest) {
                        if (world.getBlockState(side).isAir) {
                            openSide = side
                            break
                        }
                    }
                    if (openSide == BlockPos.ZERO) {
                        if (world.getBlockState(hiveLocation.above()).isAir) {
                            // If the above position is also air, we can use it
                            openSide = hiveLocation.above() // Fallback to above if no open sides found
                        } else if (world.getBlockState(hiveLocation.below()).isAir) {
                            // If the below position is also air, we can use it
                            openSide = hiveLocation.below() // Fallback to below if no open sides found
                        } else {
                            // If no open sides or above/below positions are found, we cannot proceed
                            return@Trigger false
                        }
                    }
                    val targetVec = Vec3.atCenterOf(openSide)

                    // Set path target toward hive
                    walkTarget.set(WalkTarget(targetVec, 0.35F, 0))
                    lookTarget.set(BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    return@Trigger true
                }
            }
        }
    }
}