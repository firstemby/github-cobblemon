/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.client.render.camera.compositor.CameraCompositor
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorLayout
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SideBySideLayout
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket.TransitionType
import com.mojang.blaze3d.platform.Window
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class ClientSceneCompositorTest {
    private val window = mockk<Window>()
    private val compositor = mockk<CameraCompositor>(relaxed = true)
    private var installed: CameraCompositor? = null
    private var installedId: UUID? = null
    private var installedLayout: CompositorLayout? = null
    private lateinit var session: ClientSceneSession

    @BeforeEach
    fun setup() {
        mockkStatic(Minecraft::class, ClientCameraController::class)
        mockkObject(CompositorManager)
        val client = mockk<Minecraft>()
        every { Minecraft.getInstance() } returns client
        every { client.window } returns window
        every { window.width } returns 1280
        every { window.height } returns 720
        every { ClientCameraController.start(any(), any(), any(), any()) } just Runs
        every { ClientCameraController.markInstantTransitionForScene(any()) } just Runs
        every { ClientCameraController.stop(any()) } just Runs
        every { CompositorManager.getCompositor(any()) } answers { installed }
        every { CompositorManager.createCompositor(any(), any(), any(), any(), any()) } answers {
            installedId = firstArg()
            installedLayout = args[1] as CompositorLayout
            installed = compositor
            compositor
        }
        every { CompositorManager.removeCompositor(any()) } answers { installed = null }
        session = ClientSceneSession(UUID.randomUUID(), "test", Vec3.ZERO,
            listOf(FeedDeclaration("primary", FeedKind.Fullscreen)))
    }

    @AfterEach
    fun cleanup() {
        try {
            session.stop()
        } finally {
            unmockkObject(CompositorManager)
            unmockkStatic(Minecraft::class, ClientCameraController::class)
        }
    }

    @Test
    fun `failed compositor creation retries unchanged feeds and an existing compositor follows window size`() {
        var attempts = 0
        every { CompositorManager.createCompositor(any(), any(), any(), any(), any()) } answers {
            if (++attempts == 1) throw IllegalStateException("allocation failed")
            installedId = firstArg()
            installed = compositor
            compositor
        }
        session.applyFeedAssignment("primary", "wide", TransitionType.INSTANT, 0f)
        assertNull(installed)
        session.refreshCompositor()
        assertEquals(2, attempts)
        assertTrue(session.ownsCompositor(installedId!!))

        every { window.width } returns 1920
        every { window.height } returns 1080
        session.refreshCompositor()
        verify(exactly = 1) { compositor.resize(1920, 1080) }
        assertEquals(2, attempts)
    }

    @Test
    fun `camera cleanup failure does not retain session state or skip other cameras and compositor`() {
        session.applyFeedAssignment("primary", "wide", TransitionType.INSTANT, 0f)
        val first = session.feeds.getValue("primary").feedCameraId
        val second = ClientSceneSession.FeedState()
        session.feeds["secondary"] = second
        val compositorId = installedId!!
        every { ClientCameraController.stop(first) } throws IllegalStateException("camera cleanup failed")
        session.stop()
        assertTrue(session.feeds.isEmpty())
        assertFalse(session.ownsCompositor(compositorId))
        assertNull(session.currentBeatId)
        verify(exactly = 1) { ClientCameraController.stop(second.feedCameraId) }
        verify(exactly = 1) { CompositorManager.removeCompositor(compositorId) }
        session.stop()
        verify(exactly = 1) { ClientCameraController.stop(first) }
    }

    @Test
    fun `horizontal split normalizes unequal authored shares`() {
        val layout = installSplitLayout(
            FeedDeclaration("left", FeedKind.Split(FeedKind.Side.LEFT, 0.2f)),
            FeedDeclaration("right", FeedKind.Split(FeedKind.Side.RIGHT, 0.6f))
        )

        assertFalse(layout.vertical)
        assertEquals(0.25f, layout.splitRatio, 1e-6f)
        assertEquals(session.feeds.getValue("left").feedCameraId, layout.leftCameraId)
        assertEquals(session.feeds.getValue("right").feedCameraId, layout.rightCameraId)
    }

    @Test
    fun `vertical split normalizes unequal authored shares`() {
        val layout = installSplitLayout(
            FeedDeclaration("top", FeedKind.Split(FeedKind.Side.TOP, 0.6f)),
            FeedDeclaration("bottom", FeedKind.Split(FeedKind.Side.BOTTOM, 0.2f))
        )

        assertTrue(layout.vertical)
        assertEquals(0.75f, layout.splitRatio, 1e-6f)
        assertEquals(session.feeds.getValue("top").feedCameraId, layout.leftCameraId)
        assertEquals(session.feeds.getValue("bottom").feedCameraId, layout.rightCameraId)
    }

    private fun installSplitLayout(vararg declarations: FeedDeclaration): SideBySideLayout {
        session = ClientSceneSession(UUID.randomUUID(), "test", Vec3.ZERO, declarations.toList())
        declarations.forEach { declaration ->
            session.feeds[declaration.id] = ClientSceneSession.FeedState(currentShotId = "shot")
        }
        session.refreshCompositor()
        return installedLayout as? SideBySideLayout ?: fail("Expected a split compositor layout")
    }
}
