/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.client.gui.stadium.StadiumDropdownLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumDropdownLayoutTest {
    @Test fun `menus fit every screen edge and long option lists`() {
        for ((width, height) in listOf(320 to 180, 320 to 240, 427 to 240, 640 to 360, 1920 to 1080)) {
            for (x in listOf(0, width / 2, width - 100)) for (y in listOf(0, height / 2, height - 22)) {
                for (count in listOf(1, 2, 6, 32)) {
                    val popup = StadiumDropdownLayout.fit(x, y, 100, 22, 900, count, width, height)
                    assertTrue(popup.x >= 0 && popup.y >= 0)
                    assertTrue(popup.x + popup.width <= width)
                    assertTrue(popup.y + popup.height <= height)
                    assertTrue(popup.visibleRows in 1..minOf(count, 7))
                }
            }
        }
    }

    @Test fun `menu flips above a bottom anchor`() {
        val popup = StadiumDropdownLayout.fit(20, 210, 180, 22, 180, 6, 320, 240)
        assertTrue(popup.y + popup.height < 210)
        assertEquals(6, popup.visibleRows)
    }

    @Test fun `menu opens below a top anchor`() {
        val popup = StadiumDropdownLayout.fit(20, 30, 180, 22, 180, 6, 320, 240)
        assertEquals(54, popup.y)
        assertEquals(6, popup.visibleRows)
    }

    @Test fun `padding and outside edges never select an option`() {
        val popup = StadiumDropdownLayout(10, 20, 100, 3)
        assertNull(popup.rowAt(20.0))
        assertNull(popup.rowAt(23.9))
        assertEquals(0, popup.rowAt(24.0))
        assertEquals(2, popup.rowAt(83.9))
        assertNull(popup.rowAt(84.0))
        assertFalse(popup.contains(110.0, 30.0))
        assertFalse(popup.contains(20.0, 88.0))
    }
}
