/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.SimpleMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

class SearchForEggTask : Objective {
    private var movementTask: com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation? = null
    private var remainingCooldownTicks = 20

    override fun getDescription(entity: LivingEntity) = "Search for egg"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity !is PathfinderMob || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.isFixation(movementTask)) {
            brain.sendDebugMessage("[SearchForEgg] Fixation active.")
            return ObjectiveStatus.FIXATED
        }

        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST)
        if (eggInNest != null && eggInNest == true) {
            brain.sendDebugMessage("[SearchForEgg] Egg already in nest.")
            return ObjectiveStatus.INVALID
        }

        if (brain.getMemory(CobblemonMemories.HOLDING_EGG) == true) {
            brain.sendDebugMessage("[SearchForEgg] Already holding egg.")
            return ObjectiveStatus.INVALID
        }
        if (brain.getMemory(CobblemonMemories.EGG_LOCATION) != null) {
            brain.sendDebugMessage("[SearchForEgg] Egg location already known.")
            return ObjectiveStatus.INVALID
        }
        if (brain.getMemory(CobblemonMemories.EGG_THIEF) != null) {
            brain.sendDebugMessage("[SearchForEgg] Egg thief already detected.")
            return ObjectiveStatus.INVALID
        }

        val nestPos = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val nestBlockEntity = world.getBlockEntity(nestPos) as? NestBlockEntity
        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)

        val searchCountdownRemaining = entity.brain.getTimeUntilExpiry(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        val searchCountdownActive = brain.getMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)

        if (searchCountdownRemaining <= 20 && searchCountdownActive != false && searchCountdownActive != null) {
            brain.sendDebugMessage("[SearchForEgg] Search countdown expired; clearing nest memories.")
            (world.getBlockEntity(nestPos) as? NestBlockEntity)?.parents = Pair(null, null)

            brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
            brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
            brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
            brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)

            if (nestBlockEntity != null) {
                nestBlockEntity.parents = Pair(null, null)
                nestBlockEntity.nestClaimedBy = Pair(null, null)
            }

            return ObjectiveStatus.INACTIVE
        }

        val nestCenter = Vec3.atCenterOf(nestPos)

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }
        remainingCooldownTicks = 20

        val disturbancePos = brain.getMemory(MemoryModuleType.DISTURBANCE_LOCATION)
        val targetVec = if (disturbancePos != null) {
            val disturbanceVec = Vec3.atCenterOf(disturbancePos)
            if (entity.position().distanceTo(disturbanceVec) > DISTURBANCE_MIN_DISTANCE) {
                disturbanceVec
            } else {
                null
            }
        } else {
            null
        }

        val finalTarget = targetVec ?: getRandomPositionAround(nestCenter, entity) ?: return ObjectiveStatus.ACTIVE

        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(finalTarget, 1.0F, 1)) ?: return ObjectiveStatus.ACTIVE
        brain.sendDebugMessage("[SearchForEgg] Searching near ${finalTarget}.")
        movementTask = brain.fixate(SimpleMoveFixation(tracker, "Search for egg"))

        return ObjectiveStatus.ACTIVE
    }

    private fun getRandomPositionAround(center: Vec3, entity: PathfinderMob): Vec3? {
        repeat(10) {
            val offsetX = Random.nextDouble(-SEARCH_RADIUS, SEARCH_RADIUS)
            val offsetZ = Random.nextDouble(-SEARCH_RADIUS, SEARCH_RADIUS)
            val targetVec = center.add(offsetX, 0.0, offsetZ)
            val targetPos = BlockPos(targetVec.x.toInt(), targetVec.y.toInt(), targetVec.z.toInt())
            if (entity.navigation.createPath(targetPos, 0) != null) {
                return Vec3.atCenterOf(targetPos)
            }
        }
        return null
    }

    companion object {
        private const val SEARCH_RADIUS = 32.0
        private const val DISTURBANCE_MIN_DISTANCE = 10.0
    }
}
