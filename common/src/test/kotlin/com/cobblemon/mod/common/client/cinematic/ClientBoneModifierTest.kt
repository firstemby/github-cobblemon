/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OffsetModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.api.cinematic.runtime.ActorDirector
import com.cobblemon.mod.common.api.cinematic.runtime.CameraBlockCollision
import com.cobblemon.mod.common.api.cinematic.runtime.CutResolver
import com.cobblemon.mod.common.api.cinematic.runtime.FeedRouter
import com.cobblemon.mod.common.api.cinematic.runtime.ShotEvaluator
import com.cobblemon.mod.common.client.camera.BoneFocusResolver
import com.cobblemon.mod.common.client.camera.ClientCameraController
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket.TransitionType
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.netty.buffer.Unpooled
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ClientBoneModifierTest {
    private val director = mockk<ActorDirector>()
    private val sessions = mutableListOf<ClientSceneSession>()
    private var target = Vec3(0.0, 0.0, 10.0)
    private val bonePosition = Vec3(10.0, 0.0, 0.0)
    private val look = LookAt(LookAtTarget.ActorBone("target", "head"))

    @BeforeEach
    fun setup() {
        val client = mockk<Minecraft>()
        client.level = null
        client.player = null
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns client
        val entity = mockk<Entity>()
        every { entity.id } returns 42
        every { director.resolveEntityPublic("target") } returns entity
        every { director.cameraBounds("target") } answers { AABB(target, target) }
        mockkConstructor(BoneFocusResolver::class)
        every { anyConstructed<BoneFocusResolver>().resolve(any(), any(), any(), any()) } returns bonePosition
        every { anyConstructed<BoneFocusResolver>().close() } just Runs
    }

    @AfterEach
    fun cleanup() {
        try {
            sessions.forEach { it.stop() }
        } finally {
            unmockkConstructor(BoneFocusResolver::class)
            unmockkStatic(Minecraft::class)
        }
    }

    @Test
    fun `offset and shake survive smoothing routing and packet decoding without accumulating`() {
        val shake = ShakeModifier(rotationAmplitudeDeg = 4f)
        val shot = Shot("focus", FixedPosition(Vec3.ZERO), look, modifiers = listOf(
            SmoothingModifier(rotationFactor = 0.5f), shake, OffsetModifier(yawOffset = 12f, pitchOffset = -5f)))
        val sample = sampler(shot)
        for (frame in 0..20) {
            val time = frame / 20f
            val (nx, ny) = shake.noise3(time)
            assertDisplayed(sample(time), 12f + nx * 4f, -5f + ny * 4f)
        }
    }

    @Test
    fun `rotation smoothing correction crosses yaw seam by the short arc`() {
        val shot = Shot("focus", FixedPosition(Vec3.ZERO), look,
            modifiers = listOf(SmoothingModifier(rotationFactor = 0.5f), OffsetModifier(yawOffset = 12f)))
        val sample = sampler(shot)
        target = Vec3(-0.1, 0.0, -10.0)
        val firstAim = look.computeRotation(Vec3.ZERO, target)
        sample(0f)
        target = Vec3(0.1, 0.0, -10.0)
        val secondAim = look.computeRotation(Vec3.ZERO, target)
        val lag = -Mth.wrapDegrees(secondAim.yaw - firstAim.yaw) * 0.5f
        assertDisplayed(sample(0.05f), 12f + lag, 0f)
    }

    @Test
    fun `block avoidance does not become a second angular correction on the client`() {
        val orbit = OrbitPosition(Vec3.ZERO, 10f, 0f, height = 2f)
        val wall = AABB(4.0, -2.0, -2.0, 6.0, 5.0, 2.0)
        val collision = CameraBlockCollision { query -> listOf(wall).filter { it.intersects(query) } }
        val shot = Shot("focus", orbit, look, modifiers = listOf(OffsetModifier(yawOffset = 12f, pitchOffset = -5f)))
        val feed = sampler(shot, collision)(0f)
        assertTrue(feed.position.distanceTo(orbit.evaluate(0f)) > 0.1)
        assertTrue(collision.clear(feed.position))
        assertDisplayed(feed, 12f, -5f)
    }

    private fun sampler(shot: Shot, collision: CameraBlockCollision? = null): (Float) -> FeedTransform {
        val beat = Beat("main", cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, shot.id)))))
        val scene = Scene("test", shots = mapOf(shot.id to shot), feeds = listOf(FeedDeclaration("primary", FeedKind.Fullscreen)),
            startBeatId = beat.id, beats = mapOf(beat.id to beat))
        every { director.scene } returns scene
        every { director.bindings } returns emptyMap()
        val router = FeedRouter(scene)
        val evaluator = ShotEvaluator(scene, collision)
        val id = UUID.randomUUID()
        return { time ->
            val packet = router.buildTransformPacket(id, evaluator, CutResolver(), director, null, beat, time)
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                packet.encode(buffer)
                val decoded = FeedTransformPacket.decode(buffer)
                assertEquals(id, decoded.sceneId)
                assertEquals(0, buffer.readableBytes())
                decoded.feeds.single()
            } finally {
                buffer.release()
            }
        }
    }

    private fun assertDisplayed(feed: FeedTransform, yawOffset: Float, pitchOffset: Float) {
        assertEquals(yawOffset, feed.boneHint!!.yawOffset, 0.0001f)
        assertEquals(pitchOffset, feed.boneHint.pitchOffset, 0.0001f)
        val session = ClientSceneSession(UUID.randomUUID(), "test", Vec3.ZERO).also { sessions.add(it) }
        session.applyFeedAssignment("primary", "focus", TransitionType.INSTANT, 0f)
        session.applyFeedTransform(feed)
        ClientCameraController.advanceFrame(0f)
        val actual = ClientCameraController.getActiveCameraTransform(session.feeds.getValue("primary").feedCameraId)!!
        val aim = look.computeRotation(feed.position, bonePosition)
        assertEquals(0f, Mth.wrapDegrees(actual.rotation.yaw - aim.yaw - yawOffset), 0.0001f)
        assertEquals(aim.pitch + pitchOffset, actual.rotation.pitch, 0.0001f)
        session.stop()
    }
}
