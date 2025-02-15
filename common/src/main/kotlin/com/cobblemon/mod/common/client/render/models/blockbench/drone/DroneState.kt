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
class DroneState(startAge: Int = -1, startPartialTicks: Float = 0F) : PosableState() {
    init {
        age = if (startAge >= 0) startAge else 0
        currentPartialTicks = if(startAge > 0f) startPartialTicks else 0F
    }

    override fun getEntity() = null

    override fun updatePartialTicks(partialTicks: Float) {
        currentPartialTicks += partialTicks / 2
    }

    override val schedulingTracker = SchedulingTracker()
}