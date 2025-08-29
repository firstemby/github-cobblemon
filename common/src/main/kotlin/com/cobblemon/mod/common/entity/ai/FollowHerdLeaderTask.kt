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
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveDouble
import com.cobblemon.mod.common.util.toBlockPos
import com.cobblemon.mod.common.util.toVec3d
import com.google.common.collect.ImmutableMap
import net.minecraft.core.BlockPos
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.util.HoverRandomPos
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.phys.Vec3

class FollowHerdLeaderTask : Behavior<PokemonEntity>(
    ImmutableMap.of(
        CobblemonMemories.HERD_LEADER, MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
    ),
    Int.MAX_VALUE,
    Int.MAX_VALUE
) {
    var leader: PokemonEntity? = null
    var tooFar = 8F
    var closeEnough = 4F
    var herdReactionExpiry: Long? = null

    override fun checkExtraStartConditions(level: ServerLevel, owner: PokemonEntity): Boolean {
        leader = level.getEntity(owner.brain.getMemory(CobblemonMemories.HERD_LEADER).map(UUID::fromString).orElse(null) ?: return false) as? PokemonEntity
        val definition = leader?.let { leader -> owner.behaviour.herd.bestMatchLeader(owner, leader) }
        definition?.let {
            tooFar = (it.followDistance?.endInclusive ?: owner.behaviour.herd.followDistance.endInclusive).toFloat()
            closeEnough = (it.followDistance?.start ?: owner.behaviour.herd.followDistance.start).toFloat()
        }
        return leader != null
    }

    override fun canStillUse(level: ServerLevel, entity: PokemonEntity, gameTime: Long) = leader?.isAlive == true && leader?.uuid?.toString() == entity.brain.getMemory(CobblemonMemories.HERD_LEADER).orElse(null)

    override fun tick(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        val leader = leader ?: return
        if (entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return // We're busy goin' n' shit
        }
        val moveControl = entity.moveControl as? PokemonMoveControl ?: return
        val leaderMoveControl = leader.moveControl as? PokemonMoveControl ?: return
        val shouldMoveDelayed = herdReactionExpiry?.let {
            gameTime >= it
        } ?: false
        if (leader.distanceTo(entity) > tooFar) {
//            val randomNearbyPos: BlockPos = LandRandomPos.getPosTowards(entity, 5, 3, leader.position())?.let {
//                BlockPos.containing(it)
//            } ?: leader.blockPosition()

            val herdTarget = leader.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)?.target?.currentBlockPosition()
                ?: leader.blockPosition()
            val randomHerdPos = LandRandomPos.getPos(leader, 1, 1)?.toBlockPos()
                ?: herdTarget
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = randomHerdPos,
                    speedModifier = 0.4F,
                    completionRange = closeEnough.toInt()
                )
            )
        } else if (shouldMoveDelayed) {
            // Go to the leader's walk target, roughly.
            //val walkTarget = leader.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) ?: return
//            val randomNearbyPos: BlockPos = LandRandomPos.getPosTowards(entity, 5, 3, walkTarget.target.currentPosition())?.let {
//                BlockPos.containing(it)
//            } ?: walkTarget.target.currentBlockPosition()
//            val randomNearbyPos: BlockPos = LandRandomPos.getPosTowards(entity, 1, 0, leader.position())?.let {
//                BlockPos.containing(it)
//            } ?: leader.blockPosition()

            val herdTarget = leader.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)?.target?.currentBlockPosition()
                ?: leader.blockPosition()
            val randomHerdPos = LandRandomPos.getPos(leader, tooFar.toInt(), tooFar.toInt())?.toBlockPos()
                ?: herdTarget
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = randomHerdPos,
                    speedModifier = entity.runtime.resolveDouble(entity.behaviour.moving.walk.walkSpeed).toFloat(),
                    completionRange = 1
                )
            )
            herdReactionExpiry = null // We are moving so what movement would we be waiting for
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

            if (herdReactionExpiry == null) {
                val delayTicks = (0..40).random().toLong()
                herdReactionExpiry = gameTime + delayTicks
            }
        }
    }
}