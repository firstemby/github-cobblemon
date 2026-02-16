/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import net.minecraft.world.entity.LivingEntity

class SimpleMoveFixation(
    override val movementTracker: MovementTracker,
    private val description: String
) : EntityFixation, MovementTrackerFixation {
    override fun getDescription(entity: LivingEntity) = description

    override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
        return distraction.isForce || distraction.isDamage || movementTracker.lastStatus == MovementStatus.COMPLETED
    }

    override fun onDistract(distraction: Distraction, entity: LivingEntity) {
        movementTracker.stop()
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
        return tickMovementFixation() == MovementStatus.PENDING
    }
}
