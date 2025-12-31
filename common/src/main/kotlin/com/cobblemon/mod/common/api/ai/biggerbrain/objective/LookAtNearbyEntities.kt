/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import kotlin.math.pow
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Randomly looks at nearby entities out of curiosity. Very easily distracted once doing so.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class LookAtNearbyEntities(
    val maxDistance: Double = 10.0,
    val minDuration: Int = 20,
    val maxDuration: Int = 80
) : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Look at nearby entities"
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
        }

        val nearestVisibleLivingEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            ?: return ObjectiveStatus.INVALID

        val closest = nearestVisibleLivingEntities.findClosest(
            { otherEntity -> otherEntity.distanceToSqr(entity) <= maxDistance.pow(2) && !entity.hasPassenger(otherEntity) }
        ).orElse(null)

        if (closest == null) {
            return ObjectiveStatus.INACTIVE
        } else {
            // Become fixated
            brain.fixation = IdleLookFixation(
                this,
                closest,
                (minDuration..maxDuration).random()
            )
            fixation = brain.fixation
            return ObjectiveStatus.FIXATED
        }
    }

    class IdleLookFixation(
        val objective: LookAtNearbyEntities,
        val targetEntity: LivingEntity,
        var remainingTicks: Int
    ): EntityFixation, LookingTrackerFixation {
        override var lookingTracker: LookingTracker? = null

        override fun getDescription(entity: LivingEntity): String {
            return "Looking at a nearby entity ($remainingTicks ticks left)"
        }

        override fun isDistracted(distraction: Distraction, entity: LivingEntity): Boolean {
            // Bro literally anything will distract me
            objective.fixation = null
            return true
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (targetEntity.isRemoved || entity !is PathfinderMob) {
                return false
            }
            if (lookingTracker == null) {
                lookingTracker = LookingTracker(entity, EntityTracker(targetEntity, true))
            }
            val lookingTracker = lookingTracker!!
            remainingTicks--
            lookingTracker.tick()
            return remainingTicks > 0
        }
    }
}