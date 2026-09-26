/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.AttackFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndLockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt as resolveVariableInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

class DefendNest(
    speedMultiplier: Expression = "0.5".asExpression(),
    warnRange: Expression = "15.0".asExpression(),
    private val attackRange: Expression = "8.0".asExpression(),
    disengageRange: Expression = "20.0".asExpression(),
    condition: Expression = "true".asExpression(),
    targetCondition: Expression = "true".asExpression(),
    description: Expression = "'Defend Nest'".asExpression(),
    private val maxAttackDuration: Expression = "300".asExpression(),
    private val patrolMinRadius: Int = 4,
    private val patrolMaxRadius: Int = 8
) : EntityResponseObjective(
    speedMultiplier = speedMultiplier,
    engageRange = warnRange,
    disengageRange = disengageRange,
    condition = condition,
    targetCondition = targetCondition,
    description = description
) {
    private var fixation: EntityFixation? = null
    private var scanCooldownTicks = 0
    private var cryCooldownTicks = 0
    private var patrolCooldownTicks = 0

    override fun perform(entity: LivingEntity, brain: BiggerBrain, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive)
            return ObjectiveStatus.INVALID
        // if the mon is battling then don't worry about defending (this was causing crying and particle spam lol)
        if (entity.isBattling)
            return ObjectiveStatus.INACTIVE

        val nestPos = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return stopDefending(brain) // no nest? then we stop probably
        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST) == true
        val thief = brain.getMemory(CobblemonMemories.EGG_THIEF)
        // if we have no egg to watch over and no thief then we should not defend the nest anymore and need to switch to a different task
        if (!eggInNest && thief == null)
            return stopDefending(brain)

        if (scanCooldownTicks > 0)
            scanCooldownTicks--
        if (cryCooldownTicks > 0)
            cryCooldownTicks--
        val nestCenter = Vec3.atCenterOf(nestPos)

        val attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
        if (attackTarget != null && attackTarget.uuid != trackedEntity?.uuid) {
            trackedEntity = null
            return ObjectiveStatus.BLOCKED
        }

        val tracked = trackedEntity
        if (tracked != null) {
            if (!keepTrackingPlayer(entity, brain, tracked, nestCenter)) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                brain.eraseMemory(MemoryModuleType.ANGRY_AT)
                trackedEntity = null
                fixation = null
                return ObjectiveStatus.ACTIVE
            }
            return respondToIntruder(entity, brain, tracked, nestCenter)
        }

        if (scanCooldownTicks <= 0) {
            scanCooldownTicks = SCAN_INTERVAL_TICKS
            trackedEntity = findIntruder(entity, brain, nestCenter)
            if (trackedEntity != null) {
                cryCooldownTicks = 0
                return ObjectiveStatus.ACTIVE
            }
        }
        return patrol(entity, brain, nestCenter, activeObjectiveTags)
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        // Go after any known egg thieves
        brain.subscribe(Priority.HIGH, CobblemonMemories.EGG_THIEF) { thief, _ ->
            if (entity is PokemonEntity && thief != null) {
                if (brain.fixation !is AttackFixation) {
                    trackedEntity = thief
                    attack(entity, brain, thief)
                }
            } else if (thief == null && fixation is AttackFixation && brain.isFixation(fixation)) {
                brain.distract(Distraction.FORCE)
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                brain.eraseMemory(MemoryModuleType.ANGRY_AT)
                trackedEntity = null
                fixation = null
            }
            true
        }
    }

    // stop defending the nest and remove the fixation
    private fun stopDefending(brain: BiggerBrain): ObjectiveStatus {
        if (trackedEntity != null) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            trackedEntity = null
            fixation = null
        }
        if (fixation is LookAndLockFixation && brain.isFixation(fixation))
            brain.distract(Distraction.FORCE)
        return ObjectiveStatus.INACTIVE
    }

    // various checks on if a player should be tracked still
    private fun keepTrackingPlayer(entity: PokemonEntity, brain: BiggerBrain, tracked: LivingEntity, nestCenter: Vec3): Boolean {
        if (tracked.isRemoved || !tracked.isAlive)
            return false
        if (tracked is Player && (tracked.isCreative || tracked.isSpectator))
            return false
        if (tracked is ServerPlayer && tracked.isInBattle())
            return false
        if (brain.getMemory(CobblemonMemories.EGG_THIEF)?.uuid == tracked.uuid)
            return true
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        return tracked.position().distanceTo(nestCenter) <= runtime.resolveFloat(disengageRange)
    }

    // warn players that get too close or that steal an egg
    private fun respondToIntruder(entity: PokemonEntity, brain: BiggerBrain, tracked: LivingEntity, nestCenter: Vec3): ObjectiveStatus {
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val distanceToNest = tracked.position().distanceTo(nestCenter)
        val isThief = brain.getMemory(CobblemonMemories.EGG_THIEF)?.uuid == tracked.uuid
        return when {
            isThief || distanceToNest <= runtime.resolveFloat(attackRange) -> attack(entity, brain, tracked) // stop you have violated the law!
            distanceToNest <= runtime.resolveFloat(engageRange) -> warn(entity, brain, tracked, nestCenter) // almost too close!
            else -> ObjectiveStatus.ACTIVE
        }
    }

    // force an attack fixation
    private fun attack(entity: PokemonEntity, brain: BiggerBrain, target: LivingEntity): ObjectiveStatus {
        if (fixation is AttackFixation && brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        if (!brain.distract(Distraction.FORCE))
            return ObjectiveStatus.BLOCKED
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        entity.cry()
        // todo we can use our own particles later
        (entity.level() as? ServerLevel)?.sendParticles(
            ParticleTypes.ANGRY_VILLAGER,
            entity.getRandomX(1.0),
            entity.getRandomY() + 0.5,
            entity.getRandomZ(1.0),
            4 + entity.random.nextInt(2),
            0.2 * entity.random.nextGaussian(),
            0.1 * entity.random.nextGaussian(),
            0.2 * entity.random.nextGaussian(),
            0.02
        )

        brain.setMemory(MemoryModuleType.ATTACK_TARGET, target, runtime.resolveInt(maxAttackDuration).toLong())
        brain.setMemory(MemoryModuleType.ANGRY_AT, target.uuid)
        brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        fixation = brain.fixate(
            AttackFixation(
                targetEntity = target,
                speedMultiplier = speedMultiplier,
                availableMoves = getRealTimeMoves(entity), // idk how this works but looks like Jazz lets us do this now
                distractionTypes = setOf(DistractionType.FORCE)
            )
        )
        return ObjectiveStatus.FIXATED
    }

    // cry at players that get too close to the nest
    private fun warn(entity: PokemonEntity, brain: BiggerBrain, target: LivingEntity, nestCenter: Vec3): ObjectiveStatus {
        if (cryCooldownTicks <= 0) {
            entity.cry()
            // todo replace these particles later here too
            (entity.level() as? ServerLevel)?.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                entity.getRandomX(1.0),
                entity.getRandomY() + 0.5,
                entity.getRandomZ(1.0),
                4 + entity.random.nextInt(2),
                0.2 * entity.random.nextGaussian(),
                0.1 * entity.random.nextGaussian(),
                0.2 * entity.random.nextGaussian(),
                0.02
            )
            cryCooldownTicks = CRY_COOLDOWN_TICKS + entity.random.nextInt(CRY_COOLDOWN_RANDOM_TICKS)
        }
        if (fixation is LookAndLockFixation && brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        if (!brain.distract(Distraction.FORCE))
            return ObjectiveStatus.BLOCKED
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val warnRange = runtime.resolveFloat(engageRange)
        val attackRange = runtime.resolveFloat(attackRange)
        fixation = brain.fixate(
            LookAndLockFixation(
                lookingTracker = LookingTracker.forTargetEntity(entity, target),
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE),
                condition = {
                    val distance = target.position().distanceTo(nestCenter)
                    trackedEntity?.uuid == target.uuid && distance <= warnRange && distance > attackRange
                }
            )
        )
        return ObjectiveStatus.FIXATED
    }

    // looks for player within the warn range and too close to the nest
    private fun findIntruder(entity: PokemonEntity, brain: BiggerBrain, nestCenter: Vec3): LivingEntity? {
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!runtime.resolveBoolean(condition))
            return null
        val warnRange = runtime.resolveFloat(engageRange)
        val visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return null

        // todo how should we handle creative players?
        return visible.findAll { candidate ->
            candidate is Player &&
                !(candidate is ServerPlayer && candidate.isInBattle()) &&
                candidate.position().distanceTo(nestCenter) <= warnRange &&
                runtime.withQueryValue("target", candidate.asMostSpecificMoLangValue()).resolveBoolean(targetCondition)
        }.minByOrNull { it.position().distanceToSqr(nestCenter) }
    }

    // pathfind around various spots around the nest
    private fun patrol(entity: PokemonEntity, brain: BiggerBrain, nestCenter: Vec3, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)
        if (fixation is LookAndMoveFixation && brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        if (patrolCooldownTicks > 0) {
            patrolCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }
        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val target = findPatrolPosition(world, nestCenter, entity) ?: return ObjectiveStatus.ACTIVE
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(target, speed, 1)) ?: return ObjectiveStatus.ACTIVE
        fixation = brain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE, DistractionType.COMFORT),
                description = "patrol method in DefendNest objective",
                lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(entity, tracker.walkTarget),
                movementTracker = tracker,
                onMovementComplete = { _, _ -> patrolCooldownTicks = PATROL_PAUSE_TICKS + entity.random.nextInt(PATROL_PAUSE_RANDOM_TICKS) }
            )
        )
        return ObjectiveStatus.FIXATED
    }

    // find pathfindable spot to patrol to
    private fun findPatrolPosition(world: ServerLevel, center: Vec3, entity: PokemonEntity): Vec3? {
        repeat(PATROL_ATTEMPTS) {
            val angle = world.random.nextDouble() * Math.PI * 2
            val radius = patrolMinRadius + world.random.nextInt(patrolMaxRadius - patrolMinRadius + 1)
            val candidate = center.add(Math.cos(angle) * radius, world.random.nextDouble() * 2 - 1, Math.sin(angle) * radius)
            val blockPos = BlockPos.containing(candidate)
            if (!world.getBlockState(blockPos).isSolidRender(world, blockPos) && entity.navigation.isStableDestination(blockPos)) {
                return Vec3.atCenterOf(blockPos)
            }
        }
        return null
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val warnRange: ExpressionOrEntityVariable = Either.left("15.0".asExpression())
        val attackRange: ExpressionOrEntityVariable = Either.left("8.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("20.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val maxAttackDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("'Defend Nest'".asExpression())
        val patrolMinRadius: ExpressionOrEntityVariable = Either.left("4".asExpression())
        val patrolMaxRadius: ExpressionOrEntityVariable = Either.left("8".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> {
            return listOf(
                speedMultiplier,
                warnRange,
                attackRange,
                disengageRange,
                condition,
                targetCondition,
                maxAttackDuration,
                description,
                patrolMinRadius,
                patrolMaxRadius
            ).asVariables()
        }

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            // I don't think we need any other memory than these
            behaviourConfigurationContext.addMemories(
                CobblemonMemories.PATERNAL_NEST_LOCATION,
                CobblemonMemories.EGG_IN_NEST,
                CobblemonMemories.EGG_THIEF,
                CobblemonMemories.EGG_SEARCH_COUNTDOWN,
                MemoryModuleType.ANGRY_AT,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES
            )
            return DefendNest(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                warnRange = warnRange.asSimplifiedExpression(entity),
                attackRange = attackRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = targetCondition.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity),
                maxAttackDuration = maxAttackDuration.asSimplifiedExpression(entity),
                patrolMinRadius = patrolMinRadius.resolveVariableInt(behaviourConfigurationContext.runtime),
                patrolMaxRadius = patrolMaxRadius.resolveVariableInt(behaviourConfigurationContext.runtime)
            )
        }
    }

    companion object {
        private const val SCAN_INTERVAL_TICKS = 20
        private const val CRY_COOLDOWN_TICKS = 75
        private const val CRY_COOLDOWN_RANDOM_TICKS = 50
        private const val PATROL_PAUSE_TICKS = 40
        private const val PATROL_PAUSE_RANDOM_TICKS = 40
        private const val PATROL_ATTEMPTS = 20
    }
}