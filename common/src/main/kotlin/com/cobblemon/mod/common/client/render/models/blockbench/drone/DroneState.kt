/*
 * Copyright (C) 2025 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.drone

import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState

/**
 * State for a drone Rotom attached to a stadium block.
 *
 * @author Flarkinater
 * @since February 12th, 2025
 */
class DroneState() : PosableState() {
    init {
        setPose("sleep")
        age = 0
        currentPartialTicks = 0F
    }

    override fun getEntity() = null

    override fun updatePartialTicks(partialTicks: Float) {
        currentPartialTicks += partialTicks / 2
    }

    override val schedulingTracker = SchedulingTracker()
}