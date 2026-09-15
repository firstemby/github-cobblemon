/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
import net.minecraft.world.entity.player.Inventory

data class SlotLayout(
    val offsetX: Int,
    val offsetY: Int,
    val columns: Int,
    val rows: Int,
    val slotWidth: Int = 18,
    val slotHeight: Int = 18,
    val spacingX: Int = 0,
    val spacingY: Int = 0
) {
    init {
        require(columns in 1..MAX_GRID_DIMENSION && rows in 1..MAX_GRID_DIMENSION) { "slot grid dimensions must be in 1..$MAX_GRID_DIMENSION" }
        validateAxis(offsetX, columns, slotWidth, spacingX)
        validateAxis(offsetY, rows, slotHeight, spacingY)
    }

    val slotCount: Int get() = columns * rows

    private fun validateAxis(offset: Int, count: Int, size: Int, spacing: Int) {
        require(size > 0 && spacing >= 0) { "slot size must be positive and spacing nonnegative" }
        val stride = size.toLong() + spacing
        val lastSlotOffset = (count - 1) * stride
        require(stride <= Int.MAX_VALUE && lastSlotOffset <= Int.MAX_VALUE && offset + lastSlotOffset + size <= Int.MAX_VALUE) {
            "slot grid coordinates exceed the integer range"
        }
    }

    companion object {
        const val MAX_GRID_DIMENSION = 16

        fun validateCollections(layouts: Map<String, SlotLayout>) {
            require(layouts.size <= MolangUiPacketLimits.MAX_CONTRACTS) { "too many container collections" }
            layouts.keys.forEach { name ->
                MolangUiPacketLimits.requireIdentifier(name, "collection name")
                require(name !in ShiftClickRule.PLAYER_SIDE_NAMES) { "collection name '$name' is reserved for player slots" }
            }
            // Native container packets encode slot indices as signed shorts.
            require(layouts.values.sumOf { it.slotCount.toLong() } + Inventory.INVENTORY_SIZE <= Short.MAX_VALUE + 1L) {
                "container slot indices exceed the native packet range"
            }
        }
    }
}
