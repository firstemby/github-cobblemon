/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai.competitive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AiBattleUtilsTest {
    @Test
    fun `normalizeWeatherId maps aliases to canonical ids`() {
        assertEquals("sunnyday", AiBattleUtils.normalizeWeatherId("sun"))
        assertEquals("raindance", AiBattleUtils.normalizeWeatherId("heavyrain"))
        assertEquals("sandstorm", AiBattleUtils.normalizeWeatherId("sand"))
        assertEquals("snow", AiBattleUtils.normalizeWeatherId("snowscape"))
        assertNull(AiBattleUtils.normalizeWeatherId(null))
    }

    @Test
    fun `parseTagPercent reads numeric suffix after prefix`() {
        assertEquals(50, AiBattleUtils.parseTagPercent("heal_50", "heal_"))
        assertEquals(10, AiBattleUtils.parseTagPercent("burn_10", "burn_"))
        assertNull(AiBattleUtils.parseTagPercent("heal_X", "heal_"))
        assertNull(AiBattleUtils.parseTagPercent("burn_10", "heal_"))
    }
}
