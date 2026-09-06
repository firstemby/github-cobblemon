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
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import net.minecraft.world.entity.LivingEntity

/**
 * A simple fixation that uses both a looking tracker and a movement tracker.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
open class LookAndMoveFixation(
    /** Distraction types that will be respected. */
    val distractionTypes: Set<DistractionType>,
    /** How to describe the fixation in the debug overlay. */
    val description: String,
    override val lookingTracker: LookingTracker,
    override val movementTracker: MovementTracker
): EntityFixation, LookingTrackerFixation, MovementTrackerFixation {
    override fun getDescription(entity: LivingEntity) = description

    override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
        return distraction.types.any(distractionTypes::contains)
    }

    override fun onDistract(distraction: Distraction, entity: LivingEntity) {
        movementTracker.stop()
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
        lookingTracker.tick()
        return tickMovementFixation() == MovementStatus.PENDING
    }
}