/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.block.SaccharineLeafBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.BeehiveBlock
import net.minecraft.world.phys.Vec3

object PlaceHoneyInHiveTask {
    fun create(): OneShot<in LivingEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.POLLINATED),
                it.present(CobblemonMemories.HIVE_LOCATION),
                it.absent(CobblemonMemories.HIVE_COOLDOWN)
            ).apply(it) { walkTarget, pollinated, hiveMemory, hiveCooldown ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob) {
                        return@Trigger false
                    }

                    val hiveCooldown = 1200L
                    val hiveLocation = it.get(hiveMemory)
                    val targetVec = Vec3.atCenterOf(hiveLocation)

                    // if we are not close to it then end early
                    if (entity.distanceToSqr(targetVec) > 2.0) {
                        return@Trigger false
                    }

                    val state = world.getBlockState(hiveLocation)
                    val block = state.block
                    if (block is BeehiveBlock) {
                        val currentLevel = state.getValue(BeehiveBlock.HONEY_LEVEL)
                        if (currentLevel < BeehiveBlock.MAX_HONEY_LEVELS) {
                            world.setBlock(hiveLocation, state.setValue(BeehiveBlock.HONEY_LEVEL, currentLevel + 1), 3)
                            entity.brain.setMemoryWithExpiry(CobblemonMemories.HIVE_COOLDOWN, true, hiveCooldown)
                            entity.brain.eraseMemory(CobblemonMemories.POLLINATED)
                        }
                    } else if (block is SaccharineLeafBlock) {
                        val currentAge = state.getValue(SaccharineLeafBlock.AGE)
                        if (currentAge < SaccharineLeafBlock.MAX_AGE) {
                            world.setBlock(hiveLocation, state.setValue(SaccharineLeafBlock.AGE, currentAge + 1), 3)
                            entity.brain.setMemoryWithExpiry(CobblemonMemories.HIVE_COOLDOWN, true, hiveCooldown)
                            entity.brain.eraseMemory(CobblemonMemories.POLLINATED)
                        }
                    }

                    return@Trigger true
                }
            }
        }
    }
}