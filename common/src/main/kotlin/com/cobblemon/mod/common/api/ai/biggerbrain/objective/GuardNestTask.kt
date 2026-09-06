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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class GuardNestTask : Objective {
    private var movementTask: com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation? = null
    private var remainingCooldownTicks = 0
    private var patrolTarget: Vec3? = null
    private var warnCooldownTicks = 0
    private var attackCooldownTicks = 0
    private var lastWarnedPlayerId: java.util.UUID? = null
    private var lastAttackedPlayerId: java.util.UUID? = null

    override fun getDescription(entity: LivingEntity) = "Guard nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID
        if (entity !is PathfinderMob) return ObjectiveStatus.INVALID

        if (warnCooldownTicks > 0) warnCooldownTicks--
        if (attackCooldownTicks > 0) attackCooldownTicks--

        if (brain.isFixation(movementTask)) {
            brain.sendDebugMessage("[GuardNest] Fixation active.")
            return ObjectiveStatus.FIXATED
        }

        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST)
        if (eggInNest != null && eggInNest == false) {
            return ObjectiveStatus.INVALID
        }

        val nestLocation = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val nestCenter = Vec3.atCenterOf(nestLocation)
        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)

        val visibleEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) as? NearestVisibleLivingEntities
        val playerCandidates = if (visibleEntities != null) {
            visibleEntities
                .findAll { it is Player && !it.isSpectator && !(it as Player).isCreative }
                .filterIsInstance<Player>()
        } else {
            val searchBox = AABB(
                nestCenter.x,
                nestCenter.y,
                nestCenter.z,
                nestCenter.x,
                nestCenter.y,
                nestCenter.z
            ).inflate(16.0)
            entity.level().getEntitiesOfClass(Player::class.java, searchBox) { candidate ->
                !candidate.isSpectator &&
                    !candidate.isCreative &&
                    !candidate.isInvisible &&
                    entity.hasLineOfSight(candidate)
            }
        }
        val player = playerCandidates.firstOrNull()
        val irritatedAtTarget = brain.getMemory(CobblemonMemories.IRRITATED_AT)

        if (player != null) {
            val playerDistanceSquared = player.position().distanceToSqr(nestCenter)

            if (playerDistanceSquared <= ATTACK_RADIUS * ATTACK_RADIUS) {
                if (attackCooldownTicks == 0 || lastAttackedPlayerId != player.uuid) {
                    brain.sendDebugMessage("[GuardNest] Escalation cry at player ${player.name.string}.")
                    entity.cry()
                    (entity.level() as? ServerLevel)?.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                        entity.getRandomX(1.0),
                        entity.getRandomY() + 0.5,
                        entity.getRandomZ(1.0),
                        4 + kotlin.random.Random.nextInt(2),
                        0.2 * entity.random.nextGaussian(),
                        0.1 * entity.random.nextGaussian(),
                        0.2 * entity.random.nextGaussian(),
                        0.02
                    )
                    attackCooldownTicks = ATTACK_COOLDOWN_TICKS
                    lastAttackedPlayerId = player.uuid
                }
                brain.sendDebugMessage("[GuardNest] Setting attack target to ${player.name.string}.")
                brain.setMemory(MemoryModuleType.ANGRY_AT, player.uuid)
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, player)
                return ObjectiveStatus.ACTIVE
            }

            if (playerDistanceSquared <= WARN_RADIUS * WARN_RADIUS) {
                if (irritatedAtTarget == null) {
                    brain.setMemory(CobblemonMemories.IRRITATED_AT, player.uuid)
                }
                if (warnCooldownTicks == 0 || lastWarnedPlayerId != player.uuid) {
                    brain.sendDebugMessage("[GuardNest] Warning cry at player ${player.name.string}.")
                    entity.cry()
                    (entity.level() as? ServerLevel)?.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                        entity.getRandomX(1.0),
                        entity.getRandomY() + 0.5,
                        entity.getRandomZ(1.0),
                        4 + kotlin.random.Random.nextInt(2),
                        0.2 * entity.random.nextGaussian(),
                        0.1 * entity.random.nextGaussian(),
                        0.2 * entity.random.nextGaussian(),
                        0.02
                    )
                    warnCooldownTicks = WARN_COOLDOWN_TICKS
                    lastWarnedPlayerId = player.uuid
                }
            }
        }

        if (patrolTarget != null && entity.distanceToSqr(patrolTarget) <= 2.0) {
            remainingCooldownTicks = 40 + entity.random.nextInt(40)
            patrolTarget = null
            return ObjectiveStatus.ACTIVE
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val validPosition = findPatrolPosition(world, nestCenter, entity, PATROL_MIN_RADIUS, PATROL_MAX_RADIUS)
            ?: return ObjectiveStatus.ACTIVE
        patrolTarget = validPosition

        val walkTarget = WalkTarget(validPosition, 1.0F, 1)
        val movementTracker = RegularMovementTracker.attempt(entity, walkTarget) ?: return ObjectiveStatus.ACTIVE

        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED
        brain.sendDebugMessage("[GuardNest] Wandering near nest to $validPosition.")
        movementTask = brain.fixate(SimpleMoveFixation(movementTracker, "Guard nest"))
        return ObjectiveStatus.ACTIVE
    }

    private fun findPatrolPosition(
        world: ServerLevel,
        center: Vec3,
        entity: PathfinderMob,
        minRadius: Int,
        maxRadius: Int
    ): Vec3? {
        val random = world.random
        repeat(20) {
            val angle = random.nextDouble() * Math.PI * 2
            val radius = minRadius + random.nextInt(maxRadius - minRadius + 1)
            val offsetX = kotlin.math.cos(angle) * radius
            val offsetZ = kotlin.math.sin(angle) * radius
            val offsetY = random.nextDouble() * 2 - 1
            val candidate = center.add(offsetX, offsetY, offsetZ)
            val blockPos = BlockPos(candidate.x.toInt(), candidate.y.toInt(), candidate.z.toInt())
            if (!world.getBlockState(blockPos).isSolidRender(world, blockPos) &&
                entity.navigation.isStableDestination(blockPos)
            ) {
                return Vec3.atCenterOf(blockPos)
            }
        }
        return null
    }

    companion object {
        private const val WARN_RADIUS = 12.0
        private const val ATTACK_RADIUS = 8.0
        private const val WARN_COOLDOWN_TICKS = 80
        private const val ATTACK_COOLDOWN_TICKS = 40
        private const val PATROL_MIN_RADIUS = 4
        private const val PATROL_MAX_RADIUS = 8
    }
}
