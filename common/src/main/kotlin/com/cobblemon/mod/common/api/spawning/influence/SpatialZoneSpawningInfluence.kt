/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import kotlin.math.sqrt
import net.minecraft.core.BlockPos

/**
 * A type of [ZoneSpawningInfluence] that is only applied to spawnable positions within a particular radius
 * of a position.
 *
 * @author Hiroku
 * @since March 9th, 2025
 */
class SpatialZoneSpawningInfluence(
    val pos: BlockPos,
    val radius: Float,
    override val influence: SpawningInfluence
) : ConditionalZoneSpawningInfluence {
    override fun appliesTo(spawnablePosition: SpawnablePosition): Boolean {
        return sqrt(spawnablePosition.position.distSqr(pos)) <= radius
    }
}