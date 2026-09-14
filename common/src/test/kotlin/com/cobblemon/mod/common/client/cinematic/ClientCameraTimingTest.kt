/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.client.camera.BoneFocusResolver
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket.TransitionType
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import io.mockk.every
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.OptionInstance
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ClientCameraTimingTest {
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
        try {
            sessions.forEach { it.stop() }
        } finally {
            unmockkStatic(Minecraft::class)
        }
    }

    @Test
    fun `resolved bone targets override the server rotation including an instant cut`() {
        mockkConstructor(BoneFocusResolver::class)
        every { anyConstructed<BoneFocusResolver>().resolve(any(), any(), any(), any()) } returns Vec3(10.0, 0.0, 0.0)
        every { anyConstructed<BoneFocusResolver>().close() } just Runs
        try {
            val session = session()
            val cameraId = session.feeds.getValue("primary").feedCameraId
            session.applyFeedAssignment("primary", "bone", TransitionType.INSTANT, 0f)
            session.applyFeedTransform(transform(0.0, 0f))
            ClientCameraController.setBoneFocus(cameraId, 42, "body", Vec3.ZERO, false)
            ClientCameraController.advanceFrame(0f)
            assertPose(session, 0.0, -90f)

            repeat(10) { ClientCameraController.setBoneFocus(cameraId, 42, "body", Vec3.ZERO, true) }
            verify(exactly = 0) { anyConstructed<BoneFocusResolver>().close() }
            ClientCameraController.setBoneFocus(cameraId, 42, "head", Vec3.ZERO, true)
            verify(exactly = 1) { anyConstructed<BoneFocusResolver>().close() }
            ClientCameraController.clearBoneFocus(cameraId)
            verify(exactly = 2) { anyConstructed<BoneFocusResolver>().close() }
            session.applyFeedTransform(transform(0.0, 0f))
            ClientCameraController.advanceFrame(1f)
            assertPose(session, 0.0, 0f)
        } finally {
            unmockkConstructor(BoneFocusResolver::class)
        }
    }

    @Test
    fun `roll follows authored blend duration and clears when the next shot omits it`() {
        val session = session()
        val cameraId = session.feeds.getValue("primary").feedCameraId
        session.applyFeedAssignment("primary", "first", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(transform(0.0, 0f).copy(roll = 170f))
        ClientCameraController.advanceFrame(0f)
        assertEquals(170f, ClientCameraController.getActiveCameraTransform(cameraId)!!.roll)

        session.applyFeedAssignment("primary", "cross", TransitionType.BLEND, 2f)
        session.applyFeedTransform(transform(0.0, 0f).copy(roll = -170f))
        ClientCameraController.advanceFrame(1f)
        assertEquals(0f, Mth.wrapDegrees(ClientCameraController.getActiveCameraTransform(cameraId)!!.roll - 180f), 0.001f)
        ClientCameraController.advanceFrame(1f)
        assertEquals(-170f, ClientCameraController.getActiveCameraTransform(cameraId)!!.roll)

        session.applyFeedAssignment("primary", "clear", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(transform(0.0, 0f))
        ClientCameraController.advanceFrame(0f)
        assertEquals(0f, ClientCameraController.getActiveCameraTransform(cameraId)!!.roll)
    }

    @Test
    fun `published feeds retain independent fov and roll and reset omitted lens channels`() {
        val first = session()
        val second = session()
        first.applyFeedAssignment("primary", "wide", TransitionType.INSTANT, 0f)
        second.applyFeedAssignment("primary", "close", TransitionType.INSTANT, 0f)
        val wideLens = Lens(aspectCrop = 2.4f, letterbox = 0.1f)
        val closeLens = Lens(aspectCrop = 1f, letterbox = 0.2f)
        first.applyFeedTransform(transform(0.0, 0f).copy(fov = 110f, roll = 15f, lens = wideLens))
        second.applyFeedTransform(transform(0.0, 0f).copy(fov = 40f, roll = -25f, lens = closeLens))

        val client = Minecraft.getInstance()
        val options = mockk<Options>()
        val fov = mockk<OptionInstance<Int>>()
        every { options.fov() } returns fov
        every { fov.get() } returns 90
        every { options.effectiveRenderDistance } returns 8
        Minecraft::class.java.getDeclaredField("options").apply { isAccessible = true }.set(client, options)
        val level = mockk<ClientLevel>()
        every { level.dimension() } returns Level.OVERWORLD
        client.level = level
        val published = mutableMapOf<UUID, Pair<Float, Float>>()
        val lenses = mutableMapOf<UUID, Lens?>()
        mockkStatic(CameraFeedHandler::class)
        every { CameraFeedHandler.updateCameraFeed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            published[firstArg()] = arg<Float>(6) to arg<Float>(5)
            lenses[firstArg()] = arg<Lens?>(9)
        }
        try {
            ClientCameraController.advanceFrame(0f)
            val firstId = first.feeds.getValue("primary").feedCameraId
            val secondId = second.feeds.getValue("primary").feedCameraId
            assertEquals(110f to 15f, published[firstId])
            assertEquals(40f to -25f, published[secondId])
            assertEquals(wideLens, lenses[firstId])
            assertEquals(closeLens, lenses[secondId])

            first.applyFeedAssignment("primary", "default", TransitionType.INSTANT, 0f)
            first.applyFeedTransform(transform(0.0, 0f))
            ClientCameraController.advanceFrame(0f)
            assertEquals(90f to 0f, published[firstId])
            assertEquals(40f to -25f, published[secondId])
            assertNull(lenses[firstId])
            assertEquals(closeLens, lenses[secondId])
        } finally {
            client.level = null
            unmockkStatic(CameraFeedHandler::class)
        }
    }

    @Test
    fun `fov uses authored blend duration and streamed updates preserve the blend origin`() {
        val session = session()
        session.applyFeedAssignment("primary", "initial", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(transform(0.0, 0f).copy(fov = 40f))
        ClientCameraController.advanceFrame(0f)
        assertEquals(40f, ClientCameraController.getPrimaryFovOverride())
        session.applyFeedAssignment("primary", "wide", TransitionType.BLEND, 2f)
        session.applyFeedTransform(transform(0.0, 0f).copy(fov = 80f))
        ClientCameraController.advanceFrame(1f)
        assertEquals(60f, ClientCameraController.getPrimaryFovOverride())
        session.applyFeedTransform(transform(0.0, 0f).copy(fov = 100f))
        ClientCameraController.advanceFrame(0.5f)
        assertEquals(85f, ClientCameraController.getPrimaryFovOverride())
        ClientCameraController.advanceFrame(0.5f)
        assertEquals(100f, ClientCameraController.getPrimaryFovOverride())
    }

    @Test
    fun `omitted fov blends toward the current player setting and releases the override`() {
        val options = mockk<Options>()
        val fov = mockk<OptionInstance<Int>>()
        every { options.fov() } returns fov
        every { fov.get() } returns 90
        Minecraft::class.java.getDeclaredField("options").apply { isAccessible = true }.set(Minecraft.getInstance(), options)
        val session = session()
        session.applyFeedAssignment("primary", "initial", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(transform(0.0, 0f).copy(fov = 40f))
        ClientCameraController.advanceFrame(0f)
        session.applyFeedAssignment("primary", "default", TransitionType.BLEND, 2f)
        session.applyFeedTransform(transform(0.0, 0f))
        ClientCameraController.advanceFrame(1f)
        assertEquals(65f, ClientCameraController.getPrimaryFovOverride())
        every { fov.get() } returns 100
        ClientCameraController.advanceFrame(0.5f)
        assertEquals(85f, ClientCameraController.getPrimaryFovOverride())
        ClientCameraController.advanceFrame(0.5f)
        assertNull(ClientCameraController.getPrimaryFovOverride())
        session.applyFeedAssignment("primary", "hard", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(transform(0.0, 0f).copy(fov = 75f))
        ClientCameraController.advanceFrame(0f)
        assertEquals(75f, ClientCameraController.getPrimaryFovOverride())
    }

    @Test
    fun `authored blends reach the same pose at 30 60 and 144 frames per second`() {
        for (fps in listOf(30, 60, 144)) {
            val session = session()
            session.applyFeedAssignment("primary", "orbit", TransitionType.BLEND, 1f)
            session.applyFeedTransform(transform(10.0, 90f))
            repeat(fps / 2) { ClientCameraController.advanceFrame(1f / fps) }
            assertPose(session, 5.0, 45f)
            repeat(fps / 2) { ClientCameraController.advanceFrame(1f / fps) }
            assertPose(session, 10.0, 90f)
            session.stop()
        }
    }

    @Test
    fun `streamed poses preserve blend origin and completion time`() {
        val session = session()
        session.applyFeedAssignment("primary", "orbit", TransitionType.BLEND, 1f)
        session.applyFeedTransform(transform(10.0, 90f))
        ClientCameraController.advanceFrame(0.25f)
        assertPose(session, 2.5, 22.5f)
        session.applyFeedTransform(transform(20.0, 120f))
        ClientCameraController.advanceFrame(0.25f)
        assertPose(session, 10.0, 60f)
        session.applyFeedTransform(transform(30.0, 150f))
        ClientCameraController.advanceFrame(0.5f)
        assertPose(session, 30.0, 150f)
        session.applyFeedTransform(transform(40.0, 150f))
        ClientCameraController.advanceFrame(0.1f)
        assertPose(session, 40.0, 150f)
    }

    @Test
    fun `replacement blends start at the current displayed pose`() {
        val session = session()
        session.applyFeedAssignment("primary", "first", TransitionType.BLEND, 1f)
        session.applyFeedTransform(transform(10.0, 90f))
        ClientCameraController.advanceFrame(0.5f)
        session.applyFeedAssignment("primary", "second", TransitionType.BLEND, 2f)
        session.applyFeedTransform(transform(15.0, 135f))
        ClientCameraController.advanceFrame(1f)
        assertPose(session, 10.0, 90f)
        ClientCameraController.advanceFrame(1f)
        assertPose(session, 15.0, 135f)
    }

    @Test
    fun `hard cuts and zero duration blends cancel a running blend`() {
        for (type in TransitionType.entries) {
            val session = session()
            session.applyFeedAssignment("primary", "first", TransitionType.BLEND, 2f)
            session.applyFeedTransform(transform(10.0, 90f))
            ClientCameraController.advanceFrame(0.5f)
            session.applyFeedAssignment("primary", "second", type, 0f)
            session.applyFeedTransform(transform(20.0, 160f))
            ClientCameraController.advanceFrame(0f)
            assertPose(session, 20.0, 160f)
            session.applyFeedTransform(transform(30.0, 160f))
            ClientCameraController.advanceFrame(0.1f)
            assertPose(session, 30.0, 160f)
            session.stop()
        }
    }

    @Test
    fun `a hard cut arriving before its pose still snaps when the pose arrives`() {
        val session = session()
        session.applyFeedAssignment("primary", "cut", TransitionType.INSTANT, 0f)
        ClientCameraController.advanceFrame(1f)
        assertPose(session, 0.0, 0f)
        session.applyFeedTransform(transform(20.0, 160f))
        ClientCameraController.advanceFrame(0f)
        assertPose(session, 20.0, 160f)
    }

    @Test
    fun `blend time starts with its first pose and does not advance while paused`() {
        val session = session()
        session.applyFeedAssignment("primary", "orbit", TransitionType.BLEND, 1f)
        ClientCameraController.advanceFrame(5f)
        assertPose(session, 0.0, 0f)
        session.applyFeedTransform(transform(10.0, 90f))
        ClientCameraController.advanceFrame(0.5f)
        for (delta in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            ClientCameraController.advanceFrame(delta)
            assertPose(session, 5.0, 45f)
        }
        ClientCameraController.advanceFrame(0.5f)
        assertPose(session, 10.0, 90f)
    }

    @Test
    fun `normal pose updates converge after position interpolation ends at every frame rate`() {
        for (fps in listOf(30, 60, 144)) {
            val session = session()
            session.applyFeedTransform(transform(10.0, 150f))
            repeat(fps / 2) { ClientCameraController.advanceFrame(1f / fps) }
            assertPose(session, 10.0, 60f)
            repeat(fps) { ClientCameraController.advanceFrame(1f / fps) }
            assertPose(session, 10.0, 150f)
            session.stop()
        }
    }

    @Test
    fun `authored rotation duration takes precedence over ordinary tracking speed limit`() {
        val session = session()
        session.applyFeedAssignment("primary", "fast", TransitionType.BLEND, 0.1f)
        session.applyFeedTransform(transform(10.0, 150f))
        ClientCameraController.advanceFrame(0.05f)
        assertPose(session, 5.0, 75f)
        ClientCameraController.advanceFrame(0.05f)
        assertPose(session, 10.0, 150f)
    }

    @Test
    fun `blend rotation crosses the angle boundary by the shortest path`() {
        val session = session(170f)
        session.applyFeedAssignment("primary", "cross", TransitionType.BLEND, 1f)
        session.applyFeedTransform(transform(10.0, -170f))
        ClientCameraController.advanceFrame(0.5f)
        val midpoint = requireNotNull(ClientCameraController.getActiveCameraTransform(session.feeds.getValue("primary").feedCameraId))
        assertEquals(5.0, midpoint.position.x, 0.0001)
        assertEquals(0f, Mth.wrapDegrees(midpoint.rotation.yaw - 180f), 0.001f)
        ClientCameraController.advanceFrame(0.5f)
        assertPose(session, 10.0, -170f)
    }

    private fun session(initialYaw: Float = 0f): ClientSceneSession {
        val session = ClientSceneSession(UUID.randomUUID(), "blend_test", Vec3.ZERO)
        sessions += session
        val feed = ClientSceneSession.FeedState()
        session.feeds["primary"] = feed
        ClientCameraController.updateTransform(feed.feedCameraId, Vec3.ZERO, CameraRotation(initialYaw, 0f))
        return session
    }

    private fun transform(x: Double, yaw: Float) = FeedTransform("primary", Vec3(x, 0.0, 0.0), CameraRotation(yaw, 0f))

    private fun assertPose(session: ClientSceneSession, x: Double, yaw: Float) {
        val pose = requireNotNull(ClientCameraController.getActiveCameraTransform(session.feeds.getValue("primary").feedCameraId))
        assertEquals(x, pose.position.x, 0.0001)
        assertEquals(yaw, pose.rotation.yaw, 0.001f)
        assertEquals(0f, pose.rotation.pitch, 0.001f)
    }
}
