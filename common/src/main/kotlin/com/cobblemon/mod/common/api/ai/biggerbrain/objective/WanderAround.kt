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
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.WanderType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget

/*
 * I've scuffed the hell out of this thing. The logic for what types of wandering to do is unclear, and I haven't
 * really decided whether it should be one mega function for finding a wander target and figuring out the type of
 * goal in the process or if it should be cleanly separated for each type of wander target. I've done a bit of both
 * and it's not pretty.
 */

class WanderAround(
    val condition: Expression = "true".asExpression(),
    val cooldownTicks: Int = 80,
    val allowLand: Expression = "(q.entity.behaviour.moving.walk.can_walk && !q.entity.behaviour.moving.walk.avoids_land) || q.entity.behaviour.moving.fly.can_fly".asExpression(),
    val allowWater: Expression = "q.entity.behaviour.moving.swim.can_swim_in_water && !q.entity.behaviour.moving.swim.avoids_water".asExpression(),
    val allowAir: Expression = "q.entity.behaviour.moving.fly.can_fly && !q.entity.behaviour.moving.walk.can_walk".asExpression(),
    val wanderChance: Expression = "1/(20*6)".asExpression(),
    val horizontalRange: Expression = "10".asExpression(),
    val verticalRange: Expression = "5".asExpression(),
    val speedMultiplier: Expression = "0.35".asExpression(),
    val avoidTargetingAir: Expression = "false".asExpression(),
    val minimumHeight: Expression = "0".asExpression(),
    val maximumHeight: Expression = "6".asExpression()
) : Objective {
    var fixation: EntityFixation? = null
    var remainingCooldownTicks = 10
    
    override fun getDescription(entity: LivingEntity) = "Wander around"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)
            return ObjectiveStatus.FIXATED
        } else if (entity !is PathfinderMob) {
            return ObjectiveStatus.INVALID
        } else if (Objective.OBJECTIVE_TAG_WANDERING in activeObjectiveTags) {
            return ObjectiveStatus.BLOCKED
        } else if (!brain.canDistract(idleBoredomDistraction)) {
            return ObjectiveStatus.BLOCKED
        }

        fixation = null

        val runtime = mainThreadRuntime
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

        if (!runtime.resolveBoolean(condition)) {
            return ObjectiveStatus.INACTIVE
        } else if (Objective.OBJECTIVE_TAG_WANDERING in activeObjectiveTags) {
            return ObjectiveStatus.BLOCKED
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        }

        val wanderControl = entity.brain.getMemorySafely(CobblemonMemories.WANDER_CONTROL).orElse(null)
        val wanderChance = runtime.resolveFloat(this.wanderChance) * (wanderControl?.wanderChanceMultiplier ?: 1.0F)
        if (wanderChance <= 1.0F && entity.level().random.nextFloat() > wanderChance) {
            return ObjectiveStatus.ACTIVE
        }

        val allowLand = wanderControl?.allowLand ?: runtime.resolveBoolean(allowLand)
        val allowWater = wanderControl?.allowWater ?: runtime.resolveBoolean(allowWater)
        val allowAir = wanderControl?.allowAir ?: runtime.resolveBoolean(allowAir)

        val types = mutableSetOf<WanderType>()
        if ((allowLand || allowAir) && WanderType.LAND.isValid(entity)) {
            types.add(WanderType.LAND)
        }
        if (allowWater && WanderType.WATER.isValid(entity)) {
            types.add(WanderType.WATER)
        }

        if (types.isEmpty()) {
            return ObjectiveStatus.INVALID
        }

        remainingCooldownTicks = cooldownTicks
        val walkTarget = types.firstNotNullOfOrNull {
            it.generateWalkTarget(
                entity = entity,
                horizontalRange = runtime.resolveInt(horizontalRange),
                verticalRange = runtime.resolveInt(verticalRange),
                minimumHeight = runtime.resolveInt(minimumHeight),
                maximumHeight = runtime.resolveInt(maximumHeight),
                avoidTargetingAir = runtime.resolveBoolean(avoidTargetingAir),
                speedMultiplier = runtime.resolveFloat(speedMultiplier),
                center = wanderControl?.center,
                maxAttempts = wanderControl?.maxAttempts ?: 4
            )
        } ?: return ObjectiveStatus.ACTIVE

        val movementTracker = RegularMovementTracker.attempt(entity, walkTarget)
            ?: return ObjectiveStatus.ACTIVE


        if (brain.distract(idleBoredomDistraction)) {
            movementTracker.tick()
            fixation = brain.fixate(
                LookAndMoveFixation(
                    description = "Wandering",
                    distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE),
                    lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(entity, walkTarget),
                    movementTracker = movementTracker
                )
            )
            activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING) // unsure if we need this
            return ObjectiveStatus.FIXATED
        }
        
        return ObjectiveStatus.BLOCKED
    }

    class Config : ObjectiveConfig {
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val cooldownTicks: ExpressionOrEntityVariable = Either.left("80".asExpression())
        val allowLand: ExpressionOrEntityVariable = Either.left("(q.entity.behaviour.moving.walk.can_walk && !q.entity.behaviour.moving.walk.avoids_land) || q.entity.behaviour.moving.fly.can_fly".asExpression())
        val allowWater: ExpressionOrEntityVariable = Either.left("q.entity.behaviour.moving.swim.can_swim_in_water && !q.entity.behaviour.moving.swim.avoids_water".asExpression())
        val allowAir: ExpressionOrEntityVariable = Either.left("q.entity.behaviour.moving.fly.can_fly && !q.entity.behaviour.moving.walk.can_walk".asExpression())
        val wanderChance: ExpressionOrEntityVariable = Either.left("1/(20*6)".asExpression())
        val horizontalRange: ExpressionOrEntityVariable = Either.left("10".asExpression())
        val verticalRange: ExpressionOrEntityVariable = Either.left("5".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.35".asExpression())
        val avoidTargetingAir: ExpressionOrEntityVariable = Either.left("false".asExpression())
        val minimumHeight: ExpressionOrEntityVariable = Either.left("0".asExpression())
        val maximumHeight: ExpressionOrEntityVariable = Either.left("6".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                cooldownTicks,
                allowLand,
                allowWater,
                allowAir,
                wanderChance,
                horizontalRange,
                verticalRange,
                speedMultiplier,
                avoidTargetingAir,
                minimumHeight,
                maximumHeight
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return WanderAround(
                condition = condition.asSimplifiedExpression(entity),
                cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime),
                allowLand = allowLand.asSimplifiedExpression(entity),
                allowWater = allowWater.asSimplifiedExpression(entity),
                allowAir = allowAir.asSimplifiedExpression(entity),
                wanderChance = wanderChance.asSimplifiedExpression(entity),
                horizontalRange = horizontalRange.asSimplifiedExpression(entity),
                verticalRange = verticalRange.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                avoidTargetingAir = avoidTargetingAir.asSimplifiedExpression(entity),
                minimumHeight = minimumHeight.asSimplifiedExpression(entity),
                maximumHeight = maximumHeight.asSimplifiedExpression(entity)
            )
        }
    }

    companion object {
        fun generateWalkTarget(entity: LivingEntity) : WalkTarget? {
            if (entity !is PathfinderMob) return null
            val runtime = mainThreadRuntime

            val wanderControl = entity.brain.getMemorySafely(CobblemonMemories.WANDER_CONTROL).orElse(null)
            runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

            val allowLand = wanderControl?.allowLand ?: runtime.resolveBoolean("(q.entity.behaviour.moving.walk.can_walk && !q.entity.behaviour.moving.walk.avoids_land) || q.entity.behaviour.moving.fly.can_fly".asExpression())
            val allowWater = wanderControl?.allowWater ?: runtime.resolveBoolean("q.entity.behaviour.moving.swim.can_swim_in_water && !q.entity.behaviour.moving.swim.avoids_water".asExpression())
            val allowAir = wanderControl?.allowAir ?: runtime.resolveBoolean("q.entity.behaviour.moving.fly.can_fly && !q.entity.behaviour.moving.walk.can_walk".asExpression())

            val types = mutableSetOf<WanderType>()
            if ((allowLand || allowAir) && WanderType.LAND.isValid(entity)) {
                types.add(WanderType.LAND)
            }
            if (allowWater && WanderType.WATER.isValid(entity)) {
                types.add(WanderType.WATER)
            }

            if (types.isEmpty()) {
                return null
            }

            return types.firstNotNullOfOrNull {
                it.generateWalkTarget(
                    entity = entity,
                    horizontalRange = 10,
                    verticalRange = 5,
                    minimumHeight = 0,
                    maximumHeight = 6,
                    avoidTargetingAir = false, // runtime.resolveBoolean(avoidTargetingAir),
                    speedMultiplier = 0.50f,
                    center = wanderControl?.center,
                    maxAttempts = wanderControl?.maxAttempts ?: 4
                )
            }
        }
    }
}