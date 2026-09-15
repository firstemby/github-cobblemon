/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.runtime.ActorDirector
import com.cobblemon.mod.common.api.cinematic.runtime.CutResolver
import com.cobblemon.mod.common.api.cinematic.runtime.FeedRouter
import com.cobblemon.mod.common.api.cinematic.runtime.ShotEvaluator
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedBoneHint
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class FeedLensTransportTest {
    @Test
    fun `router and packet preserve independent feed lenses and clear lens on the next shot`() {
        val wide = Lens(aspectCrop = 2.4f, letterbox = 0.1f)
        val close = Lens(aspectCrop = 1f, letterbox = 0.2f)
        fun shot(id: String, lens: Lens?) = Shot(id, FixedPosition(Vec3.ZERO), FixedRotation(CameraRotation(0f, 0f)), lens = lens)
        val beat = Beat("main", cuts = mapOf(
            "primary" to CutTrack(listOf(CutEntry(0f, "wide"), CutEntry(1f, "plain"))),
            "secondary" to CutTrack(listOf(CutEntry(0f, "close")))))
        val scene = Scene("test", startBeatId = beat.id, beats = mapOf(beat.id to beat),
            feeds = listOf(FeedDeclaration("primary", FeedKind.Fullscreen), FeedDeclaration("secondary", FeedKind.Fullscreen)),
            shots = listOf(shot("wide", wide), shot("close", close), shot("plain", null)).associateBy { it.id })
        val router = FeedRouter(scene)
        val director = ActorDirector(scene, emptyMap())
        val evaluator = ShotEvaluator(scene)
        fun sample(time: Float): List<FeedTransform> {
            val packet = router.buildTransformPacket(UUID.randomUUID(), evaluator, CutResolver(), director, null, beat, time)
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                packet.encode(buffer)
                val result = FeedTransformPacket.decode(buffer)
                assertEquals(0, buffer.readableBytes())
                assertEquals(packet.sceneId, result.sceneId)
                return result.feeds
            } finally {
                buffer.release()
            }
        }
        assertEquals(listOf(wide, close), sample(0f).map { it.lens })
        assertEquals(listOf(null, close), sample(1f).map { it.lens })
    }

    @Test
    fun `packet rejects nonfinite or out of range lens values`() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.6f)) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                FeedTransformPacket(UUID.randomUUID(), listOf(FeedTransform("primary", Vec3.ZERO, lens = Lens(2f, 0.1f)))).encode(buffer)
                buffer.setFloat(buffer.writerIndex() - 4, invalid)
                assertThrows(IllegalArgumentException::class.java) { FeedTransformPacket.decode(buffer) }
            } finally {
                buffer.release()
            }
        }
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                FeedTransformPacket(UUID.randomUUID(), listOf(FeedTransform("primary", Vec3.ZERO, lens = Lens(2f, 0.1f)))).encode(buffer)
                buffer.setFloat(buffer.writerIndex() - 8, invalid)
                assertThrows(IllegalArgumentException::class.java) { FeedTransformPacket.decode(buffer) }
            } finally {
                buffer.release()
            }
        }
    }

    @Test
    fun `feed transforms reject invalid camera and bone values`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedTransform("primary", Vec3(Double.NaN, 0.0, 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedTransform("primary", Vec3.ZERO, CameraRotation(Float.POSITIVE_INFINITY, 0f))
        }
        assertThrows(IllegalArgumentException::class.java) { FeedTransform("primary", Vec3.ZERO, fov = 0f) }
        assertThrows(IllegalArgumentException::class.java) { FeedTransform("primary", Vec3.ZERO, roll = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) {
            FeedTransform("primary", Vec3.ZERO, boneHint = FeedBoneHint(1, "", Vec3.ZERO, true))
        }
    }
}
