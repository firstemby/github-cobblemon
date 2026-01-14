/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.pokemon.ai.ToleratedHerdLeader
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.toBlockPos
import com.mojang.datafixers.util.Either
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class Herd(
    var checkTicks: Int = 60,
    var tooFar: Float = 8F,
    var closeEnough: Float = 4F,
    /** Chance per tick of following the herd leader. */
    val chance: Float = 1 / 60F,
    val pathingCooldownTicks: Int = 80
) : Objective {
    companion object {
        val moveCloserDistraction = Distraction(DistractionType.MOVE, DistractionType.COMFORT)
        val idleInHerdDistraction = Distraction(DistractionType.IDLE_BOREDOM, DistractionType.MOVE)
    }

    var fixation: EntityFixation? = null
    var leader: PokemonEntity? = null
    var ticksUntilMaintainLeader: Int = checkTicks
    var remainingPathingCooldownTicks = 0

    override fun getDescription(entity: LivingEntity) = "Herd"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        entity as PokemonEntity
        val moveControl = entity.moveControl as? PokemonMoveControl ?: return ObjectiveStatus.INVALID

        if (remainingPathingCooldownTicks > 0) {
            remainingPathingCooldownTicks--
        }

        val level = entity.level() as ServerLevel
        if (leader == null && brain.hasMemoryValue(CobblemonMemories.HERD_LEADER)) {
            leader = level.getEntity(brain.getMemory(CobblemonMemories.HERD_LEADER).let(UUID::fromString)) as? PokemonEntity
            if (leader == null) {
                brain.eraseMemory(CobblemonMemories.HERD_LEADER)
            }
        }

        if (ticksUntilMaintainLeader > 0) {
            ticksUntilMaintainLeader--
        } else if (ticksUntilMaintainLeader == 0) {
            if (brain.fixation == null || brain.isFixation(fixation)) {
                // Don't maintain the herd leader if we're busy with something else
                maintainHerdLeader(entity, brain)
            }
            ticksUntilMaintainLeader = checkTicks
        }

        if (leader?.level() != level) {
            this.leader = null
        }

        val leader = leader
        if (leader == null && brain.isFixation(fixation)) {
            brain.distract(Distraction.FORCE)
        }

        leader ?: return ObjectiveStatus.INACTIVE

        if (brain.isFixation(fixation) && fixation is MovementTrackerFixation) {
            return ObjectiveStatus.FIXATED
        }

        val leaderBrain = leader.biggerBrain ?: return ObjectiveStatus.INVALID // TODO Remove when migration complete
        val leaderFixation = leaderBrain.fixation

        val canMove = brain.canDistract(moveCloserDistraction) || brain.isFixation(fixation)
        val isMovingCloser = brain.isFixation(fixation) && fixation is LookAndMoveFixation

        val leaderMoveControl = leader.moveControl as? PokemonMoveControl ?: return ObjectiveStatus.INVALID
        if (leader.distanceTo(entity) > tooFar) {
            return when {
                isMovingCloser -> ObjectiveStatus.FIXATED // Already have the fixation to move closer
                remainingPathingCooldownTicks > 0 -> manageFixation(brain) // Still cooling down from last pathing attempt
                !canMove -> ObjectiveStatus.BLOCKED // I'm not able to distract the brain and move closer
                else -> tryMovingCloser(entity, brain, level, leader) // let's give it a fair shake then
            }
        } else if (leader.isFlying() && !entity.isFlying() && !isMovingCloser) {
            if (!entity.canFly()) {
                entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER) // We can't go where this guy's going
                this.leader = null
                return ObjectiveStatus.INACTIVE
            } else if (!canMove) {
                return ObjectiveStatus.BLOCKED
            }

            return tryMovingCloser(entity, brain, level, leader)
        } else if (leaderMoveControl.banking && !isMovingCloser) {
            if (!canMove) {
                return ObjectiveStatus.BLOCKED
            }

            if (leader.isInLiquid && !entity.isInLiquid) {
                if (remainingPathingCooldownTicks > 0) {
                    return manageFixation(brain) // Still cooling down from last pathing attempt
                }

                // We should get into water asap so we can mimic the banking
                return tryMovingCloser(entity, brain, level, leader, completionRange = 1)
            } else {
                removeSleep(entity, brain)
                entity.yRot = leader.yRot
                moveControl.startBanking(
                    forwardBlocksPerTick = leaderMoveControl.bankForwardBlocksPerTick,
                    upwardsBlocksPerTick = leaderMoveControl.bankUpwardsBlocksPerTick,
                    durationTicks = 2,
                    rightDegreesPerTick = 0F // Relying on the above yRot = leader.yRot because we dunno when we first started mimicking the banking
                )
                return ObjectiveStatus.FIXATED
            }
        } else if (leaderFixation is MovementTrackerFixation && leaderFixation.movementTracker?.lastStatus == MovementStatus.PENDING) {

            if (entity.random.nextFloat() > chance) {
                return manageFixation(brain) // Not this tick
            } else if (!canMove) {
                return ObjectiveStatus.BLOCKED
            } else if (isMovingCloser) {
                return ObjectiveStatus.FIXATED // Already moving
            }

            val leaderWalkTarget = leaderFixation.movementTracker?.walkTarget ?: return manageFixation(brain)


            // We're already closer to the walk target than the leader is, so no need to move. If anything we should get out of the way
            if (entity.distanceToSqr(leaderWalkTarget.target.currentPosition()) + closeEnough < leader.distanceToSqr(leaderWalkTarget.target.currentPosition())) {
                return manageFixation(brain)
            }

            // Go to the leader's walk target, roughly.
            val walkTarget = CobblemonWalkTarget(
                pos = leaderWalkTarget.target.currentPosition().add(getRandomOffset(entity, level)).toBlockPos(),
                speedModifier = leaderWalkTarget.speedModifier,
                nodeTypeFilter = (leaderWalkTarget as? CobblemonWalkTarget)?.nodeTypeFilter ?: { true },
                completionRange = closeEnough.toInt()
            )

            return tryMovingCloser(entity, brain, level, leader, walkTarget = walkTarget)
        } else if (leader.getCurrentPoseType() == PoseType.SLEEP) {
            // Leader is sleeping, if I'm drowsy and can sleep here then I will sleep too.
            if (entity.canSleepAt(entity.blockPosition().offset(0, -1, 0)) && brain.getMemory(CobblemonMemories.POKEMON_DROWSY) == true) {
                if (entity.random.nextFloat() < 1 / 40F) {
                    applySleep(entity, brain)
                }
            }
        } else if (brain.getMemory(CobblemonMemories.POKEMON_SLEEPING) == true) {
            // Leader is awake, so we should be awake too. Maybe.
            if (entity.random.nextFloat() < 1 / 15F) {
                removeSleep(entity, brain)
            }
        }

        return manageFixation(brain)
    }

    fun manageFixation(brain: BiggerBrain): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation == null) {
            fixation = brain.fixate(IdleInHerdFixation(this))
            return ObjectiveStatus.FIXATED
        } else if (leader != null) {
            return ObjectiveStatus.ACTIVE
        } else {
            return ObjectiveStatus.INACTIVE
        }
    }

    fun tryMovingCloser(
        entity: PokemonEntity,
        brain: BiggerBrain,
        level: ServerLevel,
        leader: PokemonEntity,
        walkTarget: WalkTarget? = null,
        completionRange: Int = closeEnough.toInt()
    ): ObjectiveStatus {
        val walkTarget = walkTarget ?: CobblemonWalkTarget(
            pos = leader.position().add(getRandomOffset(entity, level)).toBlockPos(),
            speedModifier = 0.4F,
            completionRange = completionRange
        )

        val movementTracker = RegularMovementTracker.attempt(entity, walkTarget)

        remainingPathingCooldownTicks = pathingCooldownTicks
        if (movementTracker == null) {
            // Couldn't find a path, wait a bit before trying again
            return manageFixation(brain)
        } else if (!(brain.isFixation(fixation) && brain.distract(Distraction.FORCE)) && !brain.distract(moveCloserDistraction)) {
            return manageFixation(brain) // Not likely based on checks run on callers to this function.
        }

        brain.sendDebugMessage("Moving closer, to ${movementTracker.walkTarget.target.currentBlockPosition()}")

        removeSleep(entity, brain)
        fixation = brain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.COMFORT, DistractionType.FORCE),
                lookingTracker = LookingTracker.fromBlockWalkTarget(entity, walkTarget),
                movementTracker = movementTracker,
                description = "Moving towards herd leader"
            )
        )

        return ObjectiveStatus.FIXATED
    }

    fun maintainHerdLeader(entity: PokemonEntity, brain: BiggerBrain) {
        val world = entity.level() as ServerLevel
        val leader = brain.getMemory(CobblemonMemories.HERD_LEADER)?.let(UUID::fromString).let(world::getEntity) as? PokemonEntity
        val currentLeaderDefinition = leader?.let { leader ->
            entity.behaviour.herd.toleratedLeaders.find { it.pokemon.matches(leader) }
        }
        if (leader == null || currentLeaderDefinition == null) {
            entity.brain.eraseMemory(CobblemonMemories.HERD_LEADER)
            this.leader = null
        }

        val bestLeader = getBestHerdLeader(
            currentLeader = if (leader == null || currentLeaderDefinition == null) null else (leader to currentLeaderDefinition),
            nearbyEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return,
            entity = entity
        )

        if (bestLeader != null && bestLeader != leader) {
            bestLeader.brain.eraseMemory(CobblemonMemories.HERD_LEADER)
            entity.brain.setMemory(CobblemonMemories.HERD_LEADER, bestLeader.uuid.toString())
            bestLeader.adjustHerdSize(1)
            leader?.adjustHerdSize(-1)
            this.leader = bestLeader
        }
    }

    fun getRandomOffset(entity: PokemonEntity, level: ServerLevel): Vec3 {
        val leader = leader ?: return Vec3.ZERO

        val minRange = closeEnough.toDouble()
        val maxRange = (tooFar - closeEnough / 2).toDouble()
        val randomDistance = Mth.nextDouble(level.random, minRange, maxRange)
        val randomAngle = Mth.nextDouble(level.random, 0.0, 2 * Math.PI)

        var offset = Vec3(cos(randomAngle) * randomDistance, 0.0, sin(randomAngle) * randomDistance)

        if ((entity.isInLiquid && leader.isInLiquid) || (entity.isFlying() && leader.isFlying())) {
            val verticalRange = randomDistance * 0.5
            val randomY = Mth.nextDouble(level.random, -verticalRange, verticalRange)
            offset = offset.add(0.0, randomY, 0.0)
        }

        return offset
    }


    fun applySleep(entity: PokemonEntity, brain: BiggerBrain) {
        brain.setMemory(CobblemonMemories.POKEMON_SLEEPING, true)
        entity.pokemon.status = PersistentStatusContainer(Statuses.SLEEP)
    }

    fun removeSleep(entity: PokemonEntity, brain: BiggerBrain) {
        if (brain.hasMemoryValue(CobblemonMemories.POKEMON_SLEEPING)) {
            brain.eraseMemory(CobblemonMemories.POKEMON_SLEEPING)
            val status = entity.pokemon.status
            if (status != null && status.status == Statuses.SLEEP) {
                entity.pokemon.status = null
            }
        }
    }

    /**
     * Logic used to choose the best herd leader from nearby visible entities.
     *
     * - Higher tier leaders are always preferred.
     * - Same-tier leaders that are closer are preferred except for when they're the same tier as the current leader.
     * - We don't want to exceed the target's herd size limit.
     * - We don't want to choose a leader that is themselves following a leader.
     * - Some leader definitions allow lower-level Pokémon to lead them.
     * - Leaders just have to not be in a party - pastured and wild Pokémon can mingle.
     */
    fun getBestHerdLeader(
        currentLeader: Pair<PokemonEntity, ToleratedHerdLeader>?,
        nearbyEntities: NearestVisibleLivingEntities,
        entity: PokemonEntity,
    ): PokemonEntity? {
        var bestLeader: PokemonEntity? = null
        val (currentLeaderEntity, currentLeaderDefinition) = currentLeader ?: Pair(null, null)
        var bestTier = currentLeaderDefinition?.tier?.let { it + 1 } ?: 0 // Only upgrade to a higher tier leader
        var bestDistance = Float.MAX_VALUE
        nearbyEntities.findAll {
            it is PokemonEntity &&
                    it != currentLeader &&
                    it.pokemon.storeCoordinates.get()?.store !is PartyStore &&
                    it.getHerdSize() < it.behaviour.herd.maxSize &&
                    !it.brain.hasMemoryValue(CobblemonMemories.POKEMON_DROWSY)
        }.forEach { possibleLeader ->
            possibleLeader as PokemonEntity
            val bestMatchingHerdLeader = entity.behaviour.herd.bestMatchLeader(entity, possibleLeader) ?: return@forEach
            val possibleLeaderIsAlreadyALeader = possibleLeader.brain.hasMemoryValue(CobblemonMemories.HERD_SIZE)
            val currentLeaderHerdTier = currentLeaderEntity?.getHerdTier() ?: entity.getHerdTier()
            if (currentLeaderHerdTier >= bestMatchingHerdLeader.tier) {
                return@forEach // This leader is following a leader of a higher tier, we can't replace them
            }
            val distance = entity.distanceTo(possibleLeader)
            if (bestMatchingHerdLeader.tier < bestTier) {
                return@forEach // No need to check further if this leader is worse than the best found so far
            } else if ((distance > bestDistance && !possibleLeaderIsAlreadyALeader) && bestMatchingHerdLeader.tier == bestTier) {
                return@forEach // No need to check further if this leader is farther than the best found so far, unless they are already a leader - I'd prefer to join a pack
            } else {
                bestLeader = possibleLeader
                bestDistance = distance
                bestTier = bestMatchingHerdLeader.tier
            }
        }
        return bestLeader ?: currentLeaderEntity
    }

    class IdleInHerdFixation(val objective: Herd): EntityFixation {
        override fun getDescription(entity: LivingEntity) = "Loiter around in the herd"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return when {
                distraction.isDamage || distraction.isForce -> true
                else -> !distraction.isMove
            }
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain) = objective.leader != null
    }

    class Config : ObjectiveConfig {
        // How frequently to check for whether it should herd. Probably isn't that expensive but might use this for chance.
        val checkTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val tooFar: ExpressionOrEntityVariable = Either.left("8".asExpression())
        val closeEnough: ExpressionOrEntityVariable = Either.left("4".asExpression())
        val chance: ExpressionOrEntityVariable = Either.left("1 / 60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(checkTicks, tooFar, closeEnough, chance).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            behaviourConfigurationContext.addMemories(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            behaviourConfigurationContext.addMemories(CobblemonMemories.HERD_LEADER)
            behaviourConfigurationContext.addMemories(CobblemonMemories.HERD_SIZE)
            return Herd(
                checkTicks = checkTicks.resolveInt(behaviourConfigurationContext.runtime),
                tooFar = tooFar.resolveFloat(behaviourConfigurationContext.runtime),
                closeEnough = closeEnough.resolveFloat(behaviourConfigurationContext.runtime),
                chance = chance.resolveFloat(behaviourConfigurationContext.runtime)
            )
        }
    }
}