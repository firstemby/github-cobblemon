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
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.math.pow
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.behavior.PositionTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/**
 * Randomly points toward spawn. Very easily distracted once doing so.
 *
 */
class PointToSpawn(
    val minDuration: Int = 20,
    val maxDuration: Int = 200
) : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Point towards spawn"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation != null) {
            fixation = null
            return ObjectiveStatus.BLOCKED
        } else if (!entity.brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)
            || !entity.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT)
        ) {
            return ObjectiveStatus.BLOCKED
        }

        val blockPos = Vec3.atCenterOf(entity.level().sharedSpawnPos).with(Direction.Axis.Y, entity.eyeY)
        brain.setMemory(
            MemoryModuleType.LOOK_TARGET,
            BlockPosTracker(blockPos)
        )


        // Become fixated
        fixation = brain.fixate(
            IdleLookFixation(
                this,
                blockPos,
                (minDuration..maxDuration).random()
            )
        )
        return ObjectiveStatus.FIXATED
    }

    class IdleLookFixation(
        val objective: PointToSpawn,
        val targetPosition: Vec3,
        var remainingTicks: Int
    ) : EntityFixation, LookingTrackerFixation {
        override var lookingTracker: LookingTracker? = null

        override fun getDescription(entity: LivingEntity): String {
            return "Looking at spawn position ($remainingTicks ticks left)"
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return true
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PathfinderMob) {
                return false
            }
            if (lookingTracker == null) {
                lookingTracker = LookingTracker(entity, BlockPosTracker(targetPosition))
            }
            val lookingTracker = lookingTracker!!
            remainingTicks--
            lookingTracker.tick()
            return remainingTicks > 0
        }
    }

    class Config : ObjectiveConfig {
        val minDuration: ExpressionOrEntityVariable = Either.left("20".asExpression())
        val maxDuration: ExpressionOrEntityVariable = Either.left("80".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            minDuration,
            maxDuration
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val minDuration = minDuration.resolveInt(behaviourConfigurationContext.runtime)
            val maxDuration = maxDuration.resolveInt(behaviourConfigurationContext.runtime)
            return PointToSpawn(
                minDuration = minDuration,
                maxDuration = maxDuration
            )
        }
    }
}