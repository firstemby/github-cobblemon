/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.AABB
import kotlin.math.floor

/**
 * Walks every block position inside [searchBox] and turns each solid block into one
 * full-cube [FieldPrimitive].
 *
 * @author Jackson
 * @since July 27th, 2026
 */

fun collectPrimitivesFromBlocks(
    level: BlockGetter,
    searchBox: AABB,
    shape: PrimitiveShape,
    includeFluids: Boolean = false
): List<FieldPrimitive> {
    val primitives = mutableListOf<FieldPrimitive>()

    val minX = floor(searchBox.minX).toInt()
    val maxX = floor(searchBox.maxX).toInt()
    val minY = floor(searchBox.minY).toInt()
    val maxY = floor(searchBox.maxY).toInt()
    val minZ = floor(searchBox.minZ).toInt()
    val maxZ = floor(searchBox.maxZ).toInt()

    val pos = BlockPos.MutableBlockPos()
    for (x in minX..maxX) {
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                pos.set(x, y, z)
                val state = level.getBlockState(pos)

                val isSolid = !state.getCollisionShape(level, pos).isEmpty
                val isFluid = includeFluids && !state.fluidState.isEmpty
                if (!isSolid && !isFluid) continue

                primitives.add(
                    FieldPrimitive(
                        shape = shape,
                        centerX = x + 0.5f, centerY = y + 0.5f, centerZ = z + 0.5f,
                        halfWidth = 0.5f, halfHeight = 0.5f, halfDepth = 0.5f
                    )
                )
            }
        }
    }

    return primitives
}
