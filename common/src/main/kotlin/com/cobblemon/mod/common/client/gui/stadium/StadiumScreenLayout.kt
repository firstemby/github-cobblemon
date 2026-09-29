/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

data class StadiumScreenLayout(val x: Int, val y: Int, val width: Int, val height: Int, val formatEditor: Boolean = false) {
    val sidebar get() = width >= 460 && height >= 240
    val contentTop get() = y + if (sidebar && !formatEditor) 48 else 72
    val contentBottom get() = y + height - 50
    val contentHeight get() = (contentBottom - contentTop).coerceAtLeast(1)
    val contentLeft get() = x + if (sidebar) 116 else 18
    val contentWidth get() = width - (contentLeft - x) - 22

    fun visible(rowY: Int, rowHeight: Int): Boolean = rowY >= contentTop && rowY + rowHeight <= contentBottom

    fun mapSizeAfter(headerHeight: Int) = minOf(contentWidth, contentHeight - headerHeight - 7).coerceAtLeast(24)

    companion object {
        fun fit(width: Int, height: Int, formatEditor: Boolean = false): StadiumScreenLayout {
            val panelWidth = (width - 12).coerceIn(1, 600)
            val panelHeight = (height - 12).coerceIn(1, 400)
            return StadiumScreenLayout((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight, formatEditor)
        }
    }
}
