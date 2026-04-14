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
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Swim or fly in a circle objective
 * TODO: this may just need to be included in [WanderAround]?
 */
class CircleAround(
    val poseTypes: Set<PoseType>,
    val minTurnAngleDegrees: Float,
    val maxTurnAngleDegrees: Float,
    val verticalSpeed: Float,
    val speed: Float,
    val minDurationTicks: Int = 60,
    val maxDurationTicks: Int = 180
) : Objective {
    var chosenTurnAngle = 0F
    var chosenDuration = 0
    var fixation: EntityFixation? = null
    var moveControl: PokemonMoveControl? = null

    override fun getDescription(entity: LivingEntity) = "Circling"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (entity.getCurrentPoseType() !in poseTypes) {
            return ObjectiveStatus.BLOCKED
        } else if (brain.fixation != null) {
            fixation = null
            return ObjectiveStatus.BLOCKED // Since this doesn't distract anything then if the brain is fixated at all we are blocked
        }

        // TODO: should this not be able to distract anything? relates to the above fixation comment
//        if (!brain.distract(idleBoredomDistraction)) {
//            return ObjectiveStatus.BLOCKED
//        }

        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.navigation.stop()

        // Gather a random turn angle and random duration
        this.chosenTurnAngle = entity.random.nextFloat() * (maxTurnAngleDegrees - minTurnAngleDegrees) + minTurnAngleDegrees
        if (entity.random.nextBoolean()) {
            chosenTurnAngle = -chosenTurnAngle
        }
        chosenDuration = entity.random.nextIntBetweenInclusive(minDurationTicks, maxDurationTicks)

        // Grab moveControl off the entity to use during fixation
        this.moveControl = entity.moveControl as PokemonMoveControl?


        // Become fixated
        fixation = brain.fixate(
            CircleAroundFixation(
                this,
        null,
                chosenDuration
            )
        )
        return ObjectiveStatus.ACTIVE //TODO: should this have a cooldown? Currently just having it as the pure fallback and able to be distracted by anything. I guess fishes should always swim... probably not but I'll sort that later
    }


    class CircleAroundFixation(
        val objective: CircleAround,
        override val movementTracker: MovementTracker?,
        var timer: Int
    ) : EntityFixation, MovementTrackerFixation {
        override fun getDescription(entity: LivingEntity) = "Circling around at ${this.objective.chosenTurnAngle} degrees for ${this.timer} more ticks"
        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            super.onDistract(distraction, entity)
            objective.moveControl?.stopBanking()
        }

        // can be distracted by anything
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return true
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            objective.moveControl?.startBanking(
                forwardBlocksPerTick = objective.speed,
                upwardsBlocksPerTick = objective.verticalSpeed,
                rightDegreesPerTick = objective.chosenTurnAngle,
                durationTicks = 2 //TODO: is it odd we have the check in multiple spots?
            )
            this.timer--
            if (this.timer < 0 || (entity as PokemonEntity).getCurrentPoseType() !in objective.poseTypes) {
                objective.moveControl?.stopBanking()
                return false
            }
            return true
        }
    }

    class Config : ObjectiveConfig {
        val poseTypes: Set<PoseType> = setOf(PoseType.FLY, PoseType.HOVER)
        val minAngularVelocityDegrees: ExpressionOrEntityVariable = Either.left("0.0".asExpression())
        val maxAngularVelocityDegrees: ExpressionOrEntityVariable = Either.left("1.0".asExpression())
        val speed: ExpressionOrEntityVariable = Either.left("0.6".asExpression())
        val verticalSpeed: ExpressionOrEntityVariable = Either.left("0.0".asExpression())
        val minDurationTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val maxDurationTicks: ExpressionOrEntityVariable = Either.left("180".asExpression())


        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            minAngularVelocityDegrees,
            maxAngularVelocityDegrees,
            speed,
            verticalSpeed,
            minDurationTicks,
            maxDurationTicks
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective? {
            if (entity !is PokemonEntity) return null
            return CircleAround(
                poseTypes = poseTypes,
                minTurnAngleDegrees = minAngularVelocityDegrees.resolveFloat(behaviourConfigurationContext.runtime),
                maxTurnAngleDegrees = maxAngularVelocityDegrees.resolveFloat(behaviourConfigurationContext.runtime),
                speed = speed.resolveFloat(behaviourConfigurationContext.runtime),
                verticalSpeed = verticalSpeed.resolveFloat(behaviourConfigurationContext.runtime),
                minDurationTicks = minDurationTicks.resolveInt(behaviourConfigurationContext.runtime),
                maxDurationTicks = maxDurationTicks.resolveInt(behaviourConfigurationContext.runtime)
            )
        }
    }
}