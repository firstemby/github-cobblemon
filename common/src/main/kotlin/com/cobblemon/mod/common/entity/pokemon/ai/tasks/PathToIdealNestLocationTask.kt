/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import kotlin.jvm.optionals.getOrNull

// pathing to found ideal nest location
object PathToIdealNestLocationTask {

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.absent(CobblemonMemories.NEST_LOCATION),
                it.absent(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.absent(MemoryModuleType.ATTACK_TARGET),
                it.registered(CobblemonMemories.POTENTIAL_MATE),
                it.registered(CobblemonMemories.READY_TO_BUILD_NEST),
                it.registered(CobblemonMemories.IDEAL_NEST_LOCATION),
                it.registered(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestLocation, paternalNestLocation, attackTarget, potentialMate, readyToBuildNest, idealLocation, walkTarget->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val readyToBuild = entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)
                    if (!readyToBuild) {
                        return@Trigger false
                    }

                    val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).getOrNull() ?: return@Trigger false

                    // Get the stored ideal nest location
                    val nestPos: BlockPos = entity.brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION).getOrNull()
                        ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(nestPos)

                    // check if close enough to ideal nest location
                    if (entity.distanceToSqr(nestCenter) <= 2.0) {
                        return@Trigger false
                    }

                    // Move towards the ideal nest location
                    walkTarget.set(WalkTarget(nestCenter, 1.0F, 1))
                    // todo set look target for entity

                    mate.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(nestCenter, 1.0F, 1)
                    )
                    // todo set look target for mate

                    return@Trigger true
                }
            }
        }
    }
}
