/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker

/**
 * Applied to fixations which may have movement associated with them. This is useful
 * in the case that an objective needs to know if the current fixation intends on (or is)
 * controlling their movement.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface MovementTrackerFixation {
    /** The movement tracker at the moment. */
    val movementTracker: MovementTracker?

    /** Ticks the movement tracker in a way that skips finalized movement trackers. */
    fun tickMovementFixation(): MovementStatus? {
        val movementTracker = this.movementTracker ?: return null
        return if (movementTracker.lastStatus != MovementStatus.PENDING) {
            movementTracker.lastStatus
        } else {
            movementTracker.tick()
        }
    }
}