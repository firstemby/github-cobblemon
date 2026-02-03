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
import com.cobblemon.mod.common.api.ai.*
import com.cobblemon.mod.common.api.ai.biggerbrain.*
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.AABB


/**
 * Looks for a resting place if the Pokemon is drowsy, and if available paths to it.
 *
 * @author JazzMcNade
 * @since February 3rd, 2026
 */
class MoveToRestingPlace(
    val ticksBetweenAttempts: Int = 80,
    val horizontalSearchDistance: Expression = "14.0".asExpression(),
    val verticalSearchDistance: Expression = "6.0".asExpression(),
    val onlyFromStatus: Expression = "false".asExpression(),
    val speedMultiplier: Expression = "0.4".asExpression()
) : Objective {
    var movementTask: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts
    override fun getDescription(entity: LivingEntity) = "Find a place suitable for sleeping"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        }
        brain.getMemory(CobblemonMemories.POKEMON_DROWSY)
            ?: return ObjectiveStatus.INACTIVE

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        } else {
            remainingCooldownTicks = ticksBetweenAttempts
        }

        if (brain.isFixation(movementTask)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        }

        val hasSleepStatus = entity.pokemon.status?.status === Statuses.SLEEP
        if (hasSleepStatus) return ObjectiveStatus.INACTIVE // Already sleeping

        if (entity.behaviour.resting.canSleep) {
            if (mainThreadRuntime.resolveBoolean(onlyFromStatus)) {
                return ObjectiveStatus.BLOCKED
            }

            if (entity.canSleepAt(entity.blockPosition().below())) {
                // Can sleep where we're at, no need to go anywhere
                return ObjectiveStatus.INACTIVE
            }

            val hSearchDistance = mainThreadRuntime.resolveDouble(horizontalSearchDistance)
            val vSearchDistance = mainThreadRuntime.resolveDouble(verticalSearchDistance)

            val position = entity.level()
                .getBlockPositions(AABB.ofSize(entity.position(), hSearchDistance, vSearchDistance, hSearchDistance))
                .filter(entity::canSleepAt).random(1).firstOrNull()

            if (position != null) {
                val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)
                val movementTracker = RegularMovementTracker.attempt(
                    entity = entity,
                    walkTarget = WalkTarget(
                        position,
                        speedMultiplier,
                        0
                    )
                )
                if (movementTracker != null) {
                    // Become fixated
                    brain.distract(comfortDistraction)
                    movementTask = brain.fixate(
                        fixation = MoveToRestingPlaceFixation(
                            movementTracker = movementTracker,
                        )
                    )
                }

            } else {
                return ObjectiveStatus.INACTIVE
            }
            return ObjectiveStatus.FIXATED
        }
        return ObjectiveStatus.INACTIVE
    }


    class MoveToRestingPlaceFixation(
        override val movementTracker: MovementTracker,
    ) : EntityFixation, MovementTrackerFixation {
        override fun getDescription(entity: LivingEntity) = "Moving to a resting place"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return distraction.isDamage || distraction.isForce || movementTracker.lastStatus == MovementStatus.COMPLETED
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            val movementStatus = tickMovementFixation()
            if (movementStatus == MovementStatus.COMPLETED) return false
            return movementStatus != MovementStatus.FAILED
        }
    }

    class Config : ObjectiveConfig {
        val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val horizontalSearchDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val verticalSearchDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())
        val onlyFromStatus: ExpressionOrEntityVariable = Either.left("false".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            ticksBetweenAttempts,
            horizontalSearchDistance,
            verticalSearchDistance,
            speedMultiplier,
            onlyFromStatus
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return MoveToRestingPlace(
                ticksBetweenAttempts = ticksBetweenAttempts,
                horizontalSearchDistance = horizontalSearchDistance.asSimplifiedExpression(entity),
                verticalSearchDistance = verticalSearchDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                onlyFromStatus = onlyFromStatus.asSimplifiedExpression(entity)
            )
        }
    }
}