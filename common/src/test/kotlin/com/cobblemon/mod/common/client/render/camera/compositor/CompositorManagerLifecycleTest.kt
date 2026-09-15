/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class CompositorManagerLifecycleTest {
    @Test
    fun `failed disposal does not retain fullscreen ownership or skip another compositor`() {
        val manager = CompositorManager
        val mapField = manager.javaClass.getDeclaredField("compositors").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val compositors = mapField.get(manager) as MutableMap<UUID, CameraCompositor>
        val fullscreen = manager.javaClass.getDeclaredField("fullscreenCompositorId").apply { isAccessible = true }
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val first = mockk<CameraCompositor>(relaxed = true)
        val second = mockk<CameraCompositor>(relaxed = true)
        every { first.compositorId } returns firstId
        every { second.compositorId } returns secondId
        every { first.dispose() } answers {
            assertNull(manager.fullscreenCompositorId)
            throw IllegalStateException("dispose failed")
        }
        try {
            compositors[firstId] = first
            fullscreen.set(manager, firstId)
            manager.removeCompositor(firstId)
            assertNull(manager.getCompositor(firstId))
            assertNull(manager.fullscreenCompositorId)

            compositors[firstId] = first
            compositors[secondId] = second
            fullscreen.set(manager, firstId)
            manager.clear()
            assertTrue(compositors.isEmpty())
            assertNull(manager.fullscreenCompositorId)
            verify(exactly = 2) { first.dispose() }
            verify(exactly = 1) { second.dispose() }
        } finally {
            compositors.clear()
            fullscreen.set(manager, null)
        }
    }
}
