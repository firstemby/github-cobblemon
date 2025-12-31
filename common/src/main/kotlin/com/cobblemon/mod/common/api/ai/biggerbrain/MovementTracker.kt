/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

/**
 * A wrapping around logic that maintains an entity's journey towards a specific destination. Mainly used by fixations
 * to keep track of when pathing is done, and whether it failed or succeeded. Can also be told to stop to do any
 * necessary clean up. Implemented only by [RegularMovementTracker] which has me questioning if it should be an interface
 * or just a simple class like the [LookingTracker].
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface MovementTracker {
    /** The last known status of this movement tracker. */
    var lastStatus: MovementStatus
    /** Performs a tick of movement tracking logic. Returns the current [MovementStatus]. */
    fun tick(): MovementStatus
    /** Stops the movement and does any necessary clean up. */
    fun stop()
}