/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class BoneTrackerTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        BoneTracker.clear()
    }

    @Test
    fun `a bone stays tracked until every holder releases it`() {
        BoneTracker.hold(42, "head")
        BoneTracker.hold(42, "head")

        BoneTracker.release(42, "head")
        assertTrue(BoneTracker.isTracked(42, "head"), "second holder still needs the bone")

        BoneTracker.release(42, "head")
        assertFalse(BoneTracker.isTracked(42, "head"))
        assertTrue(BoneTracker.getTrackedBones(42).isEmpty())
    }

    @Test
    fun `a render snapshot does not change when tracking ownership changes`() {
        BoneTracker.hold(42, "head")
        val snapshot = BoneTracker.getTrackedBones(42)
        BoneTracker.hold(42, "body")
        BoneTracker.release(42, "head")
        assertEquals(setOf("head"), snapshot)
        assertEquals(setOf("body"), BoneTracker.getTrackedBones(42))
    }

    @Test
    fun `releasing more than held does not resurrect or wedge tracking`() {
        BoneTracker.hold(42, "head")
        repeat(3) { BoneTracker.release(42, "head") }
        assertFalse(BoneTracker.isTracked(42, "head"))

        BoneTracker.hold(42, "head")
        assertTrue(BoneTracker.isTracked(42, "head"))
    }
}
