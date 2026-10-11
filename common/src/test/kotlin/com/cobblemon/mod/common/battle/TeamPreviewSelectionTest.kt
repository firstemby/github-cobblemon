/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.battles.TeamPreviewManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TeamPreviewSelectionTest {
    @Test
    fun `rejects negative out of range empty and excess selections`() {
        val party = listOf("first", null, "third")
        assertTrue(TeamPreviewManager.isValidSelection(party, setOf(0), 1))
        for (selection in listOf(emptySet(), setOf(-1), setOf(1), setOf(3), setOf(0, 2))) {
            assertFalse(TeamPreviewManager.isValidSelection(party, selection, 1))
        }
        assertTrue(TeamPreviewManager.isValidSelection(party, linkedSetOf(2, 0), 2))
        assertTrue(TeamPreviewManager.isValidSelection(party, emptySet(), 2, requireComplete = false))
        assertFalse(TeamPreviewManager.isValidSelection(party, setOf(-1), 2, requireComplete = false))
    }
}
