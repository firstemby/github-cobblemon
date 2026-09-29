/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ScenePacketValidationTest {

    @Test
    fun `constructors reject invalid feed assignment and scene start contracts`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedAssignmentPacket(UUID.randomUUID(), "", "shot", FeedAssignmentPacket.TransitionType.INSTANT, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedAssignmentPacket(UUID.randomUUID(), "feed", "", FeedAssignmentPacket.TransitionType.INSTANT, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedAssignmentPacket(UUID.randomUUID(), "feed", "shot", FeedAssignmentPacket.TransitionType.BLEND, Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedAssignmentPacket(UUID.randomUUID(), "feed", "shot", FeedAssignmentPacket.TransitionType.BLEND, -0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStartPacket(UUID.randomUUID(), "", Vec3.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStartPacket(UUID.randomUUID(), "scene", Vec3(Double.NaN, 0.0, 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStartPacket(UUID.randomUUID(), "scene", Vec3.ZERO, listOf(
                FeedDeclaration("feed", FeedKind.Fullscreen),
                FeedDeclaration("feed", FeedKind.Split(FeedKind.Side.RIGHT, 0.5f))
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStartPacket(UUID.randomUUID(), "scene", Vec3.ZERO,
                List(FeedDeclaration.MAX_FEEDS + 1) { FeedDeclaration("feed$it", FeedKind.Fullscreen) })
        }
    }

    @Test
    fun `decoders reject malformed scene packet fields`() {
        val assignment = newBuffer()
        try {
            assignment.writeUUID(UUID.randomUUID())
            assignment.writeUtf("feed")
            assignment.writeUtf("shot")
            assignment.writeByte(9)
            assignment.writeFloat(0f)
            assertThrows(IllegalArgumentException::class.java) { FeedAssignmentPacket.decode(assignment) }
        } finally { assignment.release() }

        val duration = newBuffer()
        try {
            duration.writeUUID(UUID.randomUUID())
            duration.writeUtf("feed")
            duration.writeUtf("shot")
            duration.writeByte(FeedAssignmentPacket.TransitionType.BLEND.ordinal)
            duration.writeFloat(Float.NEGATIVE_INFINITY)
            assertThrows(IllegalArgumentException::class.java) { FeedAssignmentPacket.decode(duration) }
        } finally { duration.release() }

        val scene = newBuffer()
        try {
            scene.writeUUID(UUID.randomUUID())
            scene.writeUtf("scene")
            scene.writeDouble(0.0)
            scene.writeDouble(Double.POSITIVE_INFINITY)
            scene.writeDouble(0.0)
            scene.writeVarInt(0)
            assertThrows(IllegalArgumentException::class.java) { SceneStartPacket.decode(scene) }
        } finally { scene.release() }

        val oversizedScene = newBuffer()
        try {
            oversizedScene.writeUUID(UUID.randomUUID())
            oversizedScene.writeUtf("scene")
            repeat(3) { oversizedScene.writeDouble(0.0) }
            oversizedScene.writeVarInt(FeedDeclaration.MAX_FEEDS + 1)
            assertThrows(IllegalArgumentException::class.java) { SceneStartPacket.decode(oversizedScene) }
        } finally { oversizedScene.release() }
    }

    @Test
    fun `scene packets round trip valid values`() {
        val assignment = FeedAssignmentPacket(
            UUID.randomUUID(), "feed", "shot", FeedAssignmentPacket.TransitionType.BLEND, 0.25f
        )
        val assignmentBuffer = newBuffer()
        try {
            assignment.encode(assignmentBuffer)
            val decoded = FeedAssignmentPacket.decode(assignmentBuffer)
            assertEquals(assignment.sceneId, decoded.sceneId)
            assertEquals(assignment.feedId, decoded.feedId)
            assertEquals(assignment.shotId, decoded.shotId)
            assertEquals(assignment.transitionType, decoded.transitionType)
            assertEquals(assignment.transitionDurationSeconds, decoded.transitionDurationSeconds)
        } finally { assignmentBuffer.release() }

        val scene = SceneStartPacket(
            UUID.randomUUID(), "scene", Vec3(4.0, 64.0, -2.0), listOf(
                FeedDeclaration("main", FeedKind.Fullscreen),
                FeedDeclaration("pip", FeedKind.Pip(FeedKind.Corner.TOP_RIGHT, 0.25f))
            )
        )
        val sceneBuffer = newBuffer()
        try {
            scene.encode(sceneBuffer)
            val decoded = SceneStartPacket.decode(sceneBuffer)
            assertEquals(scene.sceneId, decoded.sceneId)
            assertEquals(scene.sceneDefinitionId, decoded.sceneDefinitionId)
            assertEquals(scene.origin, decoded.origin)
            assertEquals(scene.feeds, decoded.feeds)
        } finally { sceneBuffer.release() }
    }

    private fun newBuffer(): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
}
