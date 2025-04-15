/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import kotlin.math.sqrt
import net.minecraft.core.BlockPos

/**
 * A type of [WorldSlicedSpawningInfluence] that is only applied to contexts within a particular radius
 * of a position.
 *
 * @author Hiroku
 * @since March 9th, 2025
 */
class WorldSlicedSpatialSpawningInfluence(
    val pos: BlockPos,
    val radius: Float,
    influence: SpawningInfluence
) : WorldSlicedSpawningInfluence(influence) {
    override fun appliesTo(context: SpawningContext): Boolean {
        return sqrt(context.position.distSqr(pos)) <= radius
    }
}