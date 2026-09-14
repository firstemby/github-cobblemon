/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.client.gui.stadium.StadiumScreenLayout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumScreenLayoutTest {
    @Test fun `panels and content fit small and large GUI scales`() {
        listOf(320 to 180, 427 to 240, 640 to 360, 1920 to 1080).forEach { (width, height) ->
            val layout = StadiumScreenLayout.fit(width, height)
            assertTrue(layout.x >= 0 && layout.y >= 0)
            assertTrue(layout.x + layout.width <= width && layout.y + layout.height <= height)
            assertTrue(layout.contentTop < layout.contentBottom)
            assertTrue(layout.contentBottom < layout.y + layout.height - 24)
            assertTrue(layout.contentLeft >= layout.x)
            assertTrue(layout.contentLeft + layout.contentWidth < layout.x + layout.width)
        }
    }

    @Test fun `navigation rail requires enough width and height`() {
        assertTrue(StadiumScreenLayout.fit(640, 360).sidebar)
        assertFalse(StadiumScreenLayout.fit(427, 240).sidebar)
        assertFalse(StadiumScreenLayout.fit(640, 240).sidebar)
    }

    @Test fun `terrain map fits below inline and stacked back controls`() {
        for ((width, height) in listOf(320 to 240, 427 to 240, 480 to 270, 640 to 360)) {
            val layout = StadiumScreenLayout.fit(width, height)
            for (headerHeight in listOf(31, 42)) {
                val size = layout.mapSizeAfter(headerHeight)
                assertTrue(layout.visible(layout.contentTop + headerHeight + 2, size))
                assertTrue(size <= layout.contentWidth)
                assertTrue(headerHeight + size + 7 <= layout.contentHeight)
            }
        }
    }

    @Test fun `partially clipped controls are not interactive`() {
        val layout = StadiumScreenLayout.fit(320, 180)
        assertFalse(layout.visible(layout.contentTop - 1, 18))
        assertFalse(layout.visible(layout.contentBottom - 17, 18))
        assertTrue(layout.visible(layout.contentTop, 18))
        assertTrue(layout.visible(layout.contentBottom - 18, 18))
    }
}
