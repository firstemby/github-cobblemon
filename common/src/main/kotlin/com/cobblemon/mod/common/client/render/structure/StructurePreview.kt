/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.state.BlockState

/** A parsed structure template ready for GUI preview rendering: its size and its non-air blocks. */
class StructurePreview(
    val size: Vec3i,
    val blocks: List<BlockEntry>
) {
    class BlockEntry(val pos: BlockPos, val state: BlockState)

    /** Position -> state lookup used by the fake level for neighbor-aware culling. */
    val stateMap: Map<BlockPos, BlockState> by lazy { blocks.associate { it.pos to it.state } }

    private data class Metrics(val cx: Float, val cy: Float, val cz: Float, val extent: Int)

    /**
     * Center and longest extent of the *actual rendered blocks*, ignoring empty/air padding in the
     * template's declared size. This is what the renderer pivots and scales around so rotation stays
     * centered on the visible model rather than on an off-center bounding box.
     */
    private val metrics: Metrics by lazy {
        if (blocks.isEmpty()) {
            return@lazy Metrics(size.x / 2f, size.y / 2f, size.z / 2f, maxOf(1, size.x, size.y, size.z))
        }
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for (b in blocks) {
            val p = b.pos
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
            minZ = minOf(minZ, p.z); maxZ = maxOf(maxZ, p.z)
        }
        // Blocks occupy unit cubes, so the far edge of the bounding box is max + 1.
        val hiX = maxX + 1; val hiY = maxY + 1; val hiZ = maxZ + 1
        Metrics(
            (minX + hiX) / 2f, (minY + hiY) / 2f, (minZ + hiZ) / 2f,
            maxOf(1, hiX - minX, hiY - minY, hiZ - minZ)
        )
    }

    val centerX: Float get() = metrics.cx
    val centerY: Float get() = metrics.cy
    val centerZ: Float get() = metrics.cz
    val fitExtent: Int get() = metrics.extent
}
