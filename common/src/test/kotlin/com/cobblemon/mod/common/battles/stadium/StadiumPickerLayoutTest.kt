/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.client.gui.stadium.StadiumPickerLayout
import com.cobblemon.mod.common.client.gui.stadium.StadiumScreenLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumPickerLayoutTest {
    @Test fun `shrinking below the catalog height leaves no rows instead of dividing by zero`() {
        for (kind in StadiumPickerLayout.Kind.entries) {
            val layout = StadiumPickerLayout(StadiumScreenLayout.fit(320, 139), kind)
            assertEquals(0, layout.bottom - layout.top)
            assertEquals(0, layout.visibleRows)
            assertEquals(0, layout.capacity)
            assertEquals(0, layout.maxOffset(271))
        }
    }

    @Test fun `tiny windows never produce negative tiles or invalid grid arithmetic`() {
        for (width in listOf(1, 12, 32, 64, 160, 320)) {
            for (height in listOf(1, 12, 64, 96, 138, 139, 140, 180)) {
                for (kind in StadiumPickerLayout.Kind.entries) {
                    val layout = StadiumPickerLayout(StadiumScreenLayout.fit(width, height), kind)
                    assertTrue(layout.left in 0..width)
                    assertTrue(layout.right in layout.left..width)
                    assertTrue(layout.top in 0..height)
                    assertTrue(layout.bottom in layout.top..height)
                    assertTrue(layout.columns > 0 && layout.rowHeight > 0)
                    assertTrue(layout.cellWidth >= 0 && layout.visibleRows >= 0)
                    assertTrue(layout.visibleRows * layout.rowHeight <= layout.bottom - layout.top)
                    assertTrue(layout.capacity == 0 || layout.cellWidth > 3 && layout.rowHeight > 3)
                    assertTrue(layout.maxOffset(271) >= 0)
                    assertTrue(!layout.hasDetail || layout.detailWidth >= 12)
                }
            }
        }
    }

    @Test fun `catalog controls fit every supported GUI size`() {
        for ((width, height) in listOf(320 to 180, 320 to 240, 480 to 270, 640 to 360, 1920 to 1080)) {
            for (kind in StadiumPickerLayout.Kind.entries) {
                val layout = StadiumPickerLayout(StadiumScreenLayout.fit(width, height), kind)
                assertTrue(layout.top < layout.bottom)
                assertTrue(layout.visibleRows * layout.rowHeight <= layout.bottom - layout.top)
                assertTrue(layout.rowHeight >= 22)
                assertTrue(!layout.hasDetail || layout.detailWidth >= 100)
                assertTrue(layout.columns * layout.cellWidth <= layout.listWidth)
                assertTrue(layout.left >= 0 && layout.right <= width)
                assertTrue(layout.bottom + 31 <= height)
            }
        }
    }

    @Test fun `grid scrolling keeps the last partial row reachable`() {
        for (kind in StadiumPickerLayout.Kind.entries) {
            val layout = StadiumPickerLayout(StadiumScreenLayout.fit(480, 270), kind)
            for (count in listOf(0, 1, 17, 271, 1025)) {
                val lastOffset = layout.maxOffset(count)
                assertTrue(lastOffset >= 0)
                assertTrue(lastOffset * layout.columns + layout.capacity >= count)
                assertTrue(layout.entryIndex(lastOffset, 0) == lastOffset * layout.columns)
            }
        }
    }

    @Test fun `format library and editor remain separated`() {
        val layout = StadiumScreenLayout.fit(640, 360, formatEditor = true)
        assertTrue(layout.sidebar)
        assertTrue(layout.contentLeft > layout.x + 99)
        assertTrue(layout.contentTop >= layout.y + 72)
        assertTrue(layout.contentHeight >= 120)
    }
}
