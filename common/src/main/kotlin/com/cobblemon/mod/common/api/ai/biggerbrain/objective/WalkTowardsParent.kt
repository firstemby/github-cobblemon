/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective


import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * Walk towards parent of same species objective
 */
class WalkTowardsParent(
    val minRange: Int,
    val maxRange: Int,
    val speed: Float
) : Objective {
    var fixation: EntityFixation? = null
    var cooldownTicks = 0

    override fun getDescription(entity: LivingEntity) = "Following parent species"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID // its gotta be a pokemon to have a parent species
        } else if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED // we are already fixated on this objectives fixation
        } else if (brain.fixation != null) {
            fixation = null // our brain has chosen another fixation
        }

        // Check to see if the cooldown has been completed
        // TODO: should cooldown checks happen first? Do we only want them ticked down
        if (cooldownTicks > 0) {
            cooldownTicks--
            return ObjectiveStatus.INACTIVE
        }

        // if no trusted adult is around then the objective is inactive
        val adult = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT) ?: return ObjectiveStatus.INACTIVE

        // If we are too close or too far then don't follow
        // TODO: the too far doesn't make that much sense to me. We don't want it treating it as a herd?
        val dist = entity.distanceTo(adult)
        if (dist > maxRange + 1 || dist < minRange) return ObjectiveStatus.INACTIVE

        // Can we comfort ourselves?
        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED

        // Get our walk target and movement tracker
        val walkTarget = WalkTarget(EntityTracker(adult, false), speed, minRange - 1)
        val movementTracker = RegularMovementTracker.attempt(entity as PathfinderMob, walkTarget)
            ?: return ObjectiveStatus.INACTIVE

        this.fixation = brain.fixate(
            LookAndMoveFixation(
                description = "Following parent",
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE), // We are very fine to ignore this movement if forced or hurt
                lookingTracker = LookingTracker.forTargetEntity(entity, adult),
                movementTracker = movementTracker,
                onMovementComplete = { _, _ -> cooldownTicks = 60 }
            )
        )
        return ObjectiveStatus.FIXATED // We are now fixated on
    }

    class Config : ObjectiveConfig {
        val minRange: ExpressionOrEntityVariable = Either.left("5".asExpression())
        val maxRange: ExpressionOrEntityVariable = Either.left("16".asExpression())
        val speed: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            minRange,
            maxRange,
            speed
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective? {
            if (entity !is PokemonEntity) return null
            return WalkTowardsParent(
                minRange = minRange.resolveInt(behaviourConfigurationContext.runtime),
                maxRange = maxRange.resolveInt(behaviourConfigurationContext.runtime),
                speed = speed.resolveFloat(behaviourConfigurationContext.runtime)
            )
        }
    }
}