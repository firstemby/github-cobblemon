/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob

class LookAndLockFixation(
    override val lookingTracker: LookingTracker?,
    /** Distraction types that will be respected. */
    val distractionTypes: Set<DistractionType>,
    val condition: () -> Boolean
) : EntityFixation, LookingTrackerFixation {


    override fun getDescription(entity: LivingEntity): String {
        return "Looking at a nearby entity (Locked)"
    }

    override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
        return distraction.types.any(distractionTypes::contains)
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
        val targetEntity = lookingTracker?.entity ?: return false
        if ((targetEntity.isRemoved) || entity !is PathfinderMob) {
            return false
        }
        val lookingTracker = lookingTracker
        lookingTracker.tick()
        return condition()
    }
}