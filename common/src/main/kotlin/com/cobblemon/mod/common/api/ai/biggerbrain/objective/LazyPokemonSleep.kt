/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
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
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * Owned lazy Pokémon will walk up to a cozy block their trainer is lingering near and nap there
 * until something interrupts them. Dwell time, search radius, and walk speed are configurable.
 *
 * @author gisketch
 * @since April 24th, 2026
 */
class LazyPokemonSleep(
    val condition: Expression = "true".asExpression(),
    val dwellTicks: Expression = "100".asExpression(),
    val spotRadius: Expression = "2.0".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression()
) : Objective {
    var walkFixation: LookAndMoveFixation? = null
    var napFixation: EntityFixation? = null
    private var lastPhase: Phase = Phase.IDLE
    private var lastCozySpot: BlockPos? = null

    override fun getDescription(entity: LivingEntity): String {
        val dwell = (entity as? PokemonEntity)?.brain?.getMemorySafely(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS)?.orElse(0) ?: 0
        val threshold = mainThreadRuntime.resolveInt(dwellTicks)
        return when (lastPhase) {
            Phase.IDLE -> "Lazy: idle (dwell $dwell/$threshold)"
            Phase.APPROACHING -> "Lazy: walking to cozy spot @ ${lastCozySpot?.toShortString() ?: "?"}"
            Phase.NAPPING -> "Lazy: napping"
        }
    }

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) return ObjectiveStatus.INVALID

        if (brain.isFixation(napFixation)) {
            lastPhase = Phase.NAPPING
            return ObjectiveStatus.FIXATED
        }
        if (brain.isFixation(walkFixation)) {
            lastPhase = Phase.APPROACHING
            return ObjectiveStatus.FIXATED
        }

        // Walk fixation is gone — either it finished (nap here) or something distracted us (bail).
        walkFixation?.let { fx ->
            val finishedNaturally = fx.movementTracker.lastStatus != MovementStatus.PENDING
            walkFixation = null
            napFixation = null
            val spot = lastCozySpot
            if (finishedNaturally && spot != null && canNap(entity, brain)) {
                return beginNap(entity, brain, spot)
            }
            wakeUp(entity)
            resetDwell(entity)
            lastPhase = Phase.IDLE
            return ObjectiveStatus.BLOCKED
        }
        if (napFixation != null) {
            napFixation = null
            wakeUp(entity)
            resetDwell(entity)
            lastPhase = Phase.IDLE
            return ObjectiveStatus.BLOCKED
        }

        if (entity.ownerUUID == null) return ObjectiveStatus.INACTIVE
        if (entity.pokemon.storeCoordinates.get()?.store !is PartyStore) return ObjectiveStatus.INACTIVE
        if (isInterrupted(entity)) {
            lastPhase = Phase.IDLE
            return ObjectiveStatus.INACTIVE
        }

        val cozySpot = brain.getMemory(CobblemonMemories.NEARBY_OWNER_COZY_SPOT)
        if (cozySpot == null) {
            lastPhase = Phase.IDLE
            return ObjectiveStatus.INACTIVE
        }
        lastCozySpot = cozySpot

        val dwellElapsed = entity.brain.getMemorySafely(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS).orElse(0)
        val dwellThreshold = mainThreadRuntime.resolveInt(dwellTicks)
        if (dwellElapsed < dwellThreshold) {
            lastPhase = Phase.IDLE
            return ObjectiveStatus.INACTIVE
        }

        if (!mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveBoolean(condition)) {
            lastPhase = Phase.IDLE
            return ObjectiveStatus.INACTIVE
        }

        val spotRadiusInt = mainThreadRuntime.resolveInt(spotRadius).coerceAtLeast(0)
        val withinSpot = entity.blockPosition().distManhattan(cozySpot) <= spotRadiusInt.coerceAtLeast(1)

        if (!brain.distract(Distraction.FORCE)) {
            lastPhase = Phase.IDLE
            return ObjectiveStatus.BLOCKED
        }

        return if (withinSpot) beginNap(entity, brain, cozySpot) else beginApproach(entity, brain, cozySpot, spotRadiusInt)
    }

    private fun canNap(entity: PokemonEntity, brain: BiggerBrain): Boolean {
        if (isInterrupted(entity)) return false
        if (brain.getMemory(CobblemonMemories.NEARBY_OWNER_COZY_SPOT) == null) return false
        return true
    }

    private fun beginApproach(
        entity: PokemonEntity,
        brain: BiggerBrain,
        cozySpot: BlockPos,
        spotRadiusInt: Int
    ): ObjectiveStatus {
        val speed = mainThreadRuntime.resolveFloat(speedMultiplier)
        val tracker = RegularMovementTracker.attempt(
            entity = entity,
            walkTarget = WalkTarget(cozySpot, speed, spotRadiusInt.coerceAtLeast(1))
        ) ?: run {
            // Couldn't path — nap in place if we're close enough.
            return if (entity.blockPosition().distManhattan(cozySpot) <= spotRadiusInt.coerceAtLeast(1) + 2) {
                beginNap(entity, brain, cozySpot)
            } else {
                lastPhase = Phase.IDLE
                ObjectiveStatus.INACTIVE
            }
        }
        val lookingTracker = LookingTracker(entity, BlockPosTracker(cozySpot))
        walkFixation = brain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE),
                description = "Lazy: walking to cozy spot @ ${cozySpot.toShortString()}",
                lookingTracker = lookingTracker,
                movementTracker = tracker
            )
        ) as LookAndMoveFixation
        lastPhase = Phase.APPROACHING
        return ObjectiveStatus.FIXATED
    }

    private fun beginNap(
        entity: PokemonEntity,
        brain: BiggerBrain,
        cozySpot: BlockPos
    ): ObjectiveStatus {
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.navigation.stop()
        assertSleepingMemories(entity)
        napFixation = brain.fixate(LazyNapFixation(this, cozySpot))
        lastCozySpot = cozySpot
        lastPhase = Phase.NAPPING
        return ObjectiveStatus.FIXATED
    }

    internal fun assertSleepingMemories(entity: PokemonEntity) {
        entity.brain.setMemory(CobblemonMemories.POKEMON_SLEEPING, true)
        entity.brain.setActiveActivityToFirstValid(listOf(CobblemonActivities.POKEMON_SLEEPING_ACTIVITY))
    }

    private fun isInterrupted(entity: PokemonEntity): Boolean {
        val b = entity.brain
        return b.getMemorySafely(MemoryModuleType.ATTACK_TARGET).isPresent ||
            b.getMemorySafely(MemoryModuleType.AVOID_TARGET).isPresent ||
            b.getMemorySafely(CobblemonMemories.POKEMON_BATTLE).isPresent ||
            b.getMemorySafely(MemoryModuleType.HURT_BY).isPresent
    }

    internal fun wakeUp(entity: LivingEntity) {
        if (entity !is PokemonEntity) return
        entity.brain.eraseMemory(CobblemonMemories.POKEMON_SLEEPING)
        entity.brain.useDefaultActivity()
    }

    internal fun resetDwell(entity: LivingEntity) {
        entity.brain.setMemory(CobblemonMemories.OWNER_NEAR_COZY_SPOT_TICKS, 0)
    }

    enum class Phase { IDLE, APPROACHING, NAPPING }

    class LazyNapFixation(
        val objective: LazyPokemonSleep,
        val cozySpot: BlockPos,
        override val movementTracker: MovementTracker? = null,
    ) : EntityFixation, MovementTrackerFixation {
        override fun getDescription(entity: LivingEntity) = "Lazy: napping"
        override fun tickMovementFixation() = MovementStatus.PENDING

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            if (distraction.isIdleBoredom || distraction.isComfort) return false
            objective.wakeUp(entity)
            objective.resetDwell(entity)
            return true
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {}

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity) return false

            if (objective.isInterrupted(entity)) {
                objective.wakeUp(entity)
                objective.resetDwell(entity)
                return false
            }
            if (brain.getMemory(CobblemonMemories.NEARBY_OWNER_COZY_SPOT) == null) {
                objective.wakeUp(entity)
                return false
            }

            objective.assertSleepingMemories(entity)
            return true
        }
    }

    class Config : ObjectiveConfig {
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val dwellTicks: ExpressionOrEntityVariable = Either.left("100".asExpression())
        val spotRadius: ExpressionOrEntityVariable = Either.left("2.0".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(condition, dwellTicks, spotRadius, speedMultiplier).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return LazyPokemonSleep(
                condition = condition.asSimplifiedExpression(entity),
                dwellTicks = dwellTicks.asSimplifiedExpression(entity),
                spotRadius = spotRadius.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }
}
