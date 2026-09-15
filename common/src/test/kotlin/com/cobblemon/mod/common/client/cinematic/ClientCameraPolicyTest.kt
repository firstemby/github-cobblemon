/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket.TransitionType
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class ClientCameraPolicyTest {
    private val sessions = mutableListOf<ClientSceneSession>()

    @BeforeEach
    fun setup() {
        val client = mockk<Minecraft>()
        client.level = null
        client.player = null
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns client
    }

    @AfterEach
    fun cleanup() {
        CinematicClientExtensions.suppressMainCamera = { false }
        try {
            sessions.forEach { it.stop() }
        } finally {
            unmockkStatic(Minecraft::class)
        }
    }

    @Test
    fun `a background scene cut cannot interrupt another scene blend or replace its hud policy`() {
        val background = session()
        val foreground = session()
        foreground.applyBeatChange("orbit", false, false, emptySet(), setOf("primary"))
        foreground.applyFeedAssignment("primary", "orbit", TransitionType.BLEND, 2f)
        foreground.applyFeedTransform(FeedTransform("primary", Vec3(20.0, 0.0, 0.0), CameraRotation(0f, 0f)))
        ClientCameraController.advanceFrame(0.5f)
        background.applyBeatChange("cut", true, true, HudElement.ALL, setOf("primary"))
        foreground.applyFeedTransform(FeedTransform("primary", Vec3(20.0, 0.0, 0.0), CameraRotation(0f, 0f)))
        ClientCameraController.advanceFrame(0.5f)
        assertEquals(10.0, ClientCameraController.getCameraTransform()!!.position.x, 0.0001)
        assertFalse(ClientCameraController.shouldHideHud())
        assertFalse(ClientCameraController.shouldRenderPlayerBody())
    }

    @Test
    fun `stopping the primary restores the remaining camera and its policy`() {
        val background = session()
        background.applyBeatChange("background", false, false, HudElement.ALL, setOf("primary"))
        val foreground = session()
        foreground.applyBeatChange("foreground", false, true, emptySet(), setOf("primary"))
        foreground.stop()
        assertTrue(ClientCameraController.isActive())
        assertTrue(ClientCameraController.shouldHideHud())
        assertFalse(ClientCameraController.shouldRenderPlayerBody())
        background.stop()
        assertFalse(ClientCameraController.isActive())
        assertFalse(ClientCameraController.shouldHideHud())
        assertTrue(ClientCameraController.shouldRenderPlayerBody())
    }

    @Test
    fun `a feed created after the beat inherits its policy and capture uses that feed policy`() {
        val first = ClientSceneSession(UUID.randomUUID(), "first", Vec3.ZERO).also { sessions += it }
        first.applyBeatChange("hidden", false, false, HudElement.ALL, setOf("primary"))
        val firstId = first.ensureFeedStarted("primary").feedCameraId
        val second = session()
        second.applyBeatChange("visible", false, true, emptySet(), setOf("primary"))
        assertTrue(ClientCameraController.shouldRenderPlayerBody())
        mockkStatic(CameraFeedHandler::class)
        try {
            every { CameraFeedHandler.getCurrentlyCapturedCamera() } returns firstId
            assertFalse(ClientCameraController.shouldRenderPlayerBody())
            assertTrue(ClientCameraController.shouldHideHud())
        } finally {
            unmockkStatic(CameraFeedHandler::class)
        }
    }

    @Test
    fun `optional camera suppression preserves feed captures and resumes normal playback`() {
        session()
        assertTrue(ClientCameraController.shouldUseCinematicCamera())
        CinematicClientExtensions.suppressMainCamera = { true }
        assertFalse(ClientCameraController.shouldUseCinematicCamera())
        mockkStatic(CameraFeedHandler::class)
        try {
            every { CameraFeedHandler.isCapturing() } returns true
            assertTrue(ClientCameraController.shouldUseCinematicCamera())
        } finally {
            unmockkStatic(CameraFeedHandler::class)
        }
        CinematicClientExtensions.suppressMainCamera = { false }
        assertTrue(ClientCameraController.shouldUseCinematicCamera())
    }

    private fun session(): ClientSceneSession =
        ClientSceneSession(UUID.randomUUID(), "policy", Vec3.ZERO).also {
            sessions += it
            it.ensureFeedStarted("primary")
        }
}
