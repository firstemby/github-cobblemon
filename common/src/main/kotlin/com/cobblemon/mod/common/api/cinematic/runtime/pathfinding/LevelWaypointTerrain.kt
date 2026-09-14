/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import kotlin.math.floor
import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class LevelWaypointTerrain(private val level: Level, private val width: Double, private val height: Double) {
    fun supported(position: Vec3): Boolean =
        ground(position, 0.1)?.let { abs(it.y - position.y) < 0.1 } == true
    private fun box(position: Vec3) = AABB(position.x - width / 2 + 0.001, position.y + 0.001,
        position.z - width / 2 + 0.001, position.x + width / 2 - 0.001, position.y + height - 0.001,
        position.z + width / 2 - 0.001)

    private fun loaded(bounds: AABB): Boolean {
        if (bounds.minY < level.minBuildHeight || bounds.maxY > level.maxBuildHeight) return false
        for (x in (floor(bounds.minX).toInt() shr 4)..(floor(bounds.maxX).toInt() shr 4)) {
            for (z in (floor(bounds.minZ).toInt() shr 4)..(floor(bounds.maxZ).toInt() shr 4)) {
                if (!level.hasChunkAt(BlockPos(x shl 4, floor(bounds.minY).toInt(), z shl 4))) return false
            }
        }
        return true
    }

    fun clear(position: Vec3): Boolean {
        val bounds = box(position)
        return loaded(bounds) && !level.containsAnyLiquid(bounds) &&
            level.getBlockCollisions(null, bounds).none { !it.isEmpty }
    }

    fun ground(position: Vec3, maxDrop: Double, stepUp: Double = 0.0): Vec3? {
        val body = box(position)
        val query = AABB(body.minX, (position.y - maxDrop - 0.05).coerceAtLeast(level.minBuildHeight.toDouble()),
            body.minZ, body.maxX, position.y + stepUp + 0.05, body.maxZ)
        if (!loaded(query)) return null
        return level.getBlockCollisions(null, query).filter { !it.isEmpty }
            .flatMap { it.toAabbs() }
            .filter { it.maxX > body.minX && it.minX < body.maxX && it.maxZ > body.minZ && it.minZ < body.maxZ }
            .map { it.maxY }
            .filter { it <= position.y + stepUp + 0.001 && it >= position.y - maxDrop - 0.001 }
            .sortedDescending()
            .map { Vec3(position.x, it, position.z) }
            .firstOrNull { clear(it) }
    }
}
