/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

data class StadiumDropdownLayout(val x: Int, val y: Int, val width: Int, val visibleRows: Int) {
    val height get() = visibleRows * ROW_HEIGHT + PADDING * 2
    fun contains(mouseX: Double, mouseY: Double) = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    fun rowAt(mouseY: Double): Int? = ((mouseY - y - PADDING) / ROW_HEIGHT).toInt()
        .takeIf { mouseY >= y + PADDING && mouseY < y + height - PADDING && it in 0 until visibleRows }

    companion object {
        const val ROW_HEIGHT = 20
        const val PADDING = 4

        fun fit(x: Int, y: Int, anchorWidth: Int, anchorHeight: Int, desiredWidth: Int, count: Int, screenWidth: Int, screenHeight: Int): StadiumDropdownLayout {
            val below = screenHeight - 4 - (y + anchorHeight + 2)
            val above = y - 6
            val desiredHeight = minOf(count, 7) * ROW_HEIGHT + PADDING * 2
            val down = below >= desiredHeight || below >= above
            val space = if (down) below else above
            val rows = minOf(count, 7, ((space - PADDING * 2) / ROW_HEIGHT).coerceAtLeast(1)).coerceAtLeast(0)
            val width = desiredWidth.coerceIn(1, (screenWidth - 8).coerceAtLeast(1))
            val height = rows * ROW_HEIGHT + PADDING * 2
            val top = if (down) y + anchorHeight + 2 else y - height - 2
            return StadiumDropdownLayout((x + anchorWidth - width).coerceIn(4, (screenWidth - width - 4).coerceAtLeast(4)),
                top.coerceIn(4, (screenHeight - height - 4).coerceAtLeast(4)), width, rows)
        }
    }
}
