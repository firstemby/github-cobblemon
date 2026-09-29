/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

data class StadiumPickerLayout(val panel: StadiumScreenLayout, val kind: Kind) {
    enum class Kind { POKEMON, MOVES, ABILITIES, ITEMS }

    val compact get() = panel.height < 220
    val left get() = panel.x + minOf(16, panel.width / 2)
    val right get() = (panel.x + panel.width - 16).coerceAtLeast(left)
    val top get() = panel.y + minOf(84, panel.height)
    val bottom get() = (panel.y + panel.height - 43).coerceAtLeast(top)
    val hasDetail get() = (kind == Kind.POKEMON || kind == Kind.MOVES) && right - left >= 240 && bottom - top >= 24
    val listWidth get() = if (hasDetail) (right - left) * 56 / 100 else right - left
    val detailLeft get() = left + listWidth + 8
    val detailWidth get() = (right - detailLeft).coerceAtLeast(0)
    val columns get() = when (kind) {
        Kind.POKEMON -> ((listWidth - 6) / 32).coerceAtLeast(2)
        Kind.ITEMS -> ((listWidth - 6) / 32).coerceAtLeast(1)
        Kind.MOVES -> 1
        Kind.ABILITIES -> ((listWidth - 6) / 140).coerceAtLeast(1)
    }
    val cellWidth get() = (listWidth - 6).coerceAtLeast(0) / columns
    val rowHeight get() = minOf(bottom - top, when (kind) {
        Kind.POKEMON -> cellWidth.coerceAtMost(56)
        Kind.ITEMS -> 32
        Kind.MOVES -> 36
        Kind.ABILITIES -> 46
    }).coerceAtLeast(1)
    val visibleRows get() = if (cellWidth < 4 || rowHeight < 4) 0 else (bottom - top) / rowHeight
    val capacity get() = visibleRows * columns
    fun totalRows(count: Int) = (count + columns - 1) / columns
    fun maxOffset(count: Int) = if (visibleRows == 0) 0 else (totalRows(count) - visibleRows).coerceAtLeast(0)
    fun entryIndex(rowOffset: Int, cell: Int) = rowOffset * columns + cell
}
