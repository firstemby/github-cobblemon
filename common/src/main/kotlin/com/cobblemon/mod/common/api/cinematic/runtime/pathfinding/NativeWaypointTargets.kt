/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

internal object NativeWaypointTargets {
    fun candidates(target: Vec3, width: Float, canFinish: (Vec3, Vec3) -> Boolean): Set<BlockPos> {
        // Native nodes anchor the hitbox corner, not the authored feet position.
        val offset = floor(width + 1.0) * 0.5
        val centerX = floor(target.x - offset + 0.5).toInt()
        val centerZ = floor(target.z - offset + 0.5).toInt()
        val candidates = mutableListOf<Pair<BlockPos, Double>>()
        for (x in centerX - 1..centerX + 1) {
            for (z in centerZ - 1..centerZ + 1) {
                for (y in floor(target.y).toInt()..ceil(target.y).toInt()) {
                    val point = Vec3(x + offset, y.toDouble(), z + offset)
                    if (point.subtract(target).horizontalDistanceSqr() <= 1.5 * 1.5 && canFinish(point, target)) {
                        candidates += BlockPos(x, y, z) to point.distanceToSqr(target)
                    }
                }
            }
        }
        return candidates.sortedBy { it.second }.mapTo(linkedSetOf()) { it.first }
    }

    fun searchRanges(distance: Double): List<Int> {
        val initial = (ceil(distance).toInt() + 12).coerceIn(24, 80)
        return listOf(initial, (initial * 2).coerceAtMost(80), 80).distinct()
    }
}
