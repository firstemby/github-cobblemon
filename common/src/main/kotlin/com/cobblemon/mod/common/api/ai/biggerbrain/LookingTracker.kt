/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.PositionTracker
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * A trivial wrapper around an entity and a position tracker to make the entity look at the position each tick.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class LookingTracker(val entity: PathfinderMob, val lookTarget: PositionTracker) {
    companion object {
        fun fromBlockWalkTarget(entity: PathfinderMob, walkTarget: WalkTarget): LookingTracker {
            return LookingTracker(entity, BlockPosTracker(walkTarget.target.currentPosition().add(0.0, entity.eyeHeight.toDouble(), 0.0)))
        }
    }
    fun tick() {
        entity.lookControl.setLookAt(lookTarget.currentPosition())
    }
}