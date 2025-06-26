/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.google.common.collect.ImmutableMap
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

class FollowHerdLeaderTask(
    val tooFar: Float = 16F,
    val closeEnough: Float = 8F
) : Behavior<PokemonEntity>(
    ImmutableMap.of(CobblemonMemories.HERD_LEADER, MemoryStatus.VALUE_PRESENT),
    0,
    0
) {
    var leader: PokemonEntity? = null

    override fun checkExtraStartConditions(level: ServerLevel, owner: PokemonEntity): Boolean {
        leader = level.getEntity(owner.brain.getMemory(CobblemonMemories.HERD_LEADER).map(UUID::fromString).orElse(null) ?: return false) as? PokemonEntity
        return leader != null
    }

    override fun canStillUse(level: ServerLevel, entity: PokemonEntity, gameTime: Long) = leader?.isAlive == true

    override fun tick(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        val leader = leader ?: return
        if (entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return // We're busy goin' n' shit
        }
        val moveControl = entity.moveControl as? PokemonMoveControl ?: return
        val leaderMoveControl = leader.moveControl as? PokemonMoveControl ?: return
        if (leader.distanceTo(entity) > tooFar) {
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = leader.blockPosition(),
                    speedModifier = 0.4F,
                    completionRange = closeEnough.toInt()
                )
            )
        } else if (leader.isFlying() && !entity.isFlying()) {
            if (!entity.canFly()) {
                entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER) // We can't go where this guy's going
                return
            }
            entity.setFlying(true)
            entity.addDeltaMovement(Vec3(0.0, 2.0, 0.0))
        } else if (leaderMoveControl.banking) {
            if (leader.isInLiquid && !entity.isInLiquid) {
                // We should get into water asap so we can mimic the banking
                entity.brain.setMemory(
                    MemoryModuleType.WALK_TARGET,
                    CobblemonWalkTarget(
                        pos = leader.blockPosition(),
                        speedModifier = 0.4F,
                        completionRange = 0
                    )
                )
            } else {
                entity.yRot = leader.yRot
                moveControl.startBanking(
                    forwardBlocksPerTick = leaderMoveControl.bankForwardBlocksPerTick,
                    upwardsBlocksPerTick = leaderMoveControl.bankUpwardsBlocksPerTick,
                    durationTicks = 2,
                    rightDegreesPerTick = 0F // Relying on the above yRot = leader.yRot because we dunno when we first started mimicking the banking
                )
            }
        } else if (leader.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            // Go to the leader's walk target, roughly.
            val walkTarget = leader.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) ?: return
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = walkTarget.target.currentBlockPosition(),
                    speedModifier = walkTarget.speedModifier,
                    nodeTypeFilter = (walkTarget as? CobblemonWalkTarget)?.nodeTypeFilter ?: { true },
                    completionRange = closeEnough.toInt()
                )
            )
        }
    }
}