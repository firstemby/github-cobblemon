/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class StadiumSpectatorArea(val min: Vec3, val max: Vec3) {
    val bounds: AABB get() = AABB(min, max)

    fun contains(position: Vec3) = bounds.contains(position)

    fun valid(origin: Vec3): Boolean =
        listOf(min.x, min.y, min.z, max.x, max.y, max.z).all { it.isFinite() } &&
            max.x - min.x in 1.0..128.0 && max.z - min.z in 1.0..128.0 && max.y - min.y in 2.0..64.0 &&
            listOf(min.x, max.x).all { x -> listOf(min.y, max.y).all { y ->
                listOf(min.z, max.z).all { z -> StadiumConfiguration.validPosition(Vec3(x, y, z), origin) }
            } }

    fun toNbt() = CompoundTag().apply {
        putDouble("MinX", min.x); putDouble("MinY", min.y); putDouble("MinZ", min.z)
        putDouble("MaxX", max.x); putDouble("MaxY", max.y); putDouble("MaxZ", max.z)
    }

    companion object {
        const val MAX_AREAS = 8

        fun fromCorners(first: Vec3, second: Vec3) = StadiumSpectatorArea(
            Vec3(floor(min(first.x, second.x)), min(first.y, second.y) - 0.05, floor(min(first.z, second.z))),
            Vec3(floor(max(first.x, second.x)) + 1, max(first.y, second.y) + 3, floor(max(first.z, second.z)) + 1)
        )

        fun fromNbt(tag: CompoundTag) = StadiumSpectatorArea(
            Vec3(tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ")),
            Vec3(tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ"))
        )

        fun aimPoint(arena: StadiumArena, eye: Vec3, direction: Vec3): Vec3? {
            val bounds = AABB(arena.minX, arena.center.y, arena.minZ, arena.maxX, arena.center.y + 8, arena.maxZ)
            if (bounds.contains(eye)) return null
            return bounds.clip(eye, eye.add(direction.normalize().scale(192.0))).orElse(null)
        }
    }
}
