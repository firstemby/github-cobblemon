/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.editor

import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewAckS2CPacket
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewStateS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewControlC2SPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStartC2SPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class EditorPreviewPacketRoundTripTest {

    @Test fun `preview states reject nonfinite time and bounds`() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                EditorPreviewStateS2CPacket(UUID.randomUUID(), 1L, "beat", invalid.toFloat(), true)
            }
            val buffer = newBuffer()
            try {
                buffer.writeUUID(UUID.randomUUID())
                buffer.writeLong(1L)
                buffer.writeUtf("beat")
                buffer.writeFloat(0f)
                buffer.writeBoolean(true)
                buffer.writeBoolean(false)
                buffer.writeVarInt(1)
                buffer.writeUtf("actor")
                buffer.writeDouble(invalid)
                repeat(5) { buffer.writeDouble(0.0) }
                assertThrows(IllegalArgumentException::class.java) { EditorPreviewStateS2CPacket.decode(buffer) }
            } finally { buffer.release() }
        }
    }

    @Test fun `preview states reject duplicate actors and oversized bounds collections`() {
        val buffer = newBuffer()
        try {
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeLong(1L)
            buffer.writeUtf("beat")
            buffer.writeFloat(0f)
            buffer.writeBoolean(true)
            buffer.writeBoolean(false)
            val countIndex = buffer.writerIndex()
            buffer.writeVarInt(2)
            repeat(2) {
                buffer.writeUtf("actor")
                repeat(6) { buffer.writeDouble(0.0) }
            }
            assertThrows(IllegalArgumentException::class.java) { EditorPreviewStateS2CPacket.decode(buffer) }
            buffer.readerIndex(0)
            buffer.writerIndex(countIndex)
            buffer.writeVarInt(EditorPreviewStateS2CPacket.MAX_ACTORS + 1)
            assertThrows(IllegalArgumentException::class.java) { EditorPreviewStateS2CPacket.decode(buffer) }
        } finally { buffer.release() }
    }

    private fun newBuffer(): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)

    @Test
    fun `start packet round-trips through encode and decode`() {
        val original = EditorPreviewStartC2SPacket(
            previewId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            sceneJson = """{"version":2,"sceneId":"test:scene","startBeatId":"main","beats":{}}""",
            origin = Vec3(10.5, 64.0, -3.0),
            bindings = listOf(
                SerializedBinding.Pokemon("hero", "pikachu", listOf("shiny")),
                SerializedBinding.Bedrock("prop", "cobblemon:generic", emptyList(), 1.0f)
            ),
            beatId = "second",
            time = 3.5f,
            paused = true
        )
        val buf = newBuffer()
        try {
            original.encode(buf)
            val decoded = EditorPreviewStartC2SPacket.decode(buf)

            assertEquals(original.previewId, decoded.previewId)
            assertEquals(original.sceneJson, decoded.sceneJson)
            assertEquals(original.origin, decoded.origin)
            assertEquals(original.bindings, decoded.bindings)
            assertEquals(original.beatId, decoded.beatId)
            assertEquals(original.time, decoded.time)
            assertEquals(original.paused, decoded.paused)
        } finally { buf.release() }
    }

    @Test
    fun `control and state packets retain revision beat time and pause`() {
        val id = UUID.randomUUID()
        val control = EditorPreviewControlC2SPacket(id, 42L, "second", 3.5f, true)
        val controlBuffer = newBuffer()
        try {
            control.encode(controlBuffer)
            val decoded = EditorPreviewControlC2SPacket.decode(controlBuffer)
            assertEquals(id, decoded.previewId)
            assertEquals(42L, decoded.revision)
            assertEquals("second", decoded.beatId)
            assertEquals(3.5f, decoded.time)
            assertEquals(true, decoded.paused)
        } finally { controlBuffer.release() }
        val stateBuffer = newBuffer()
        try {
            val bounds = mapOf("trainer" to AABB(10.0, 64.0, -2.0, 11.0, 66.0, -1.0))
            EditorPreviewStateS2CPacket(id, 42L, "second", 3.5f, true, Component.translatable("cobblemon.cinematic_editor.route.stuck", 2), bounds).encode(stateBuffer)
            val state = EditorPreviewStateS2CPacket.decode(stateBuffer)
            assertEquals(id, state.previewId)
            assertEquals(42L, state.revision)
            assertEquals("second", state.beatId)
            assertEquals(3.5f, state.time)
            assertEquals(true, state.paused)
            assertEquals(Component.translatable("cobblemon.cinematic_editor.route.stuck", 2), state.error)
            assertEquals(bounds, state.actorBounds)
        } finally { stateBuffer.release() }
    }

    @Test
    fun `ack packet round-trips when ok with sceneId`() {
        val original = EditorPreviewAckS2CPacket(
            previewId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            ok = true,
            sceneId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            errorMessage = null
        )
        val buf = newBuffer()
        try {
            original.encode(buf)
            val decoded = EditorPreviewAckS2CPacket.decode(buf)

            assertEquals(original.previewId, decoded.previewId)
            assertEquals(true, decoded.ok)
            assertEquals(original.sceneId, decoded.sceneId)
            assertNull(decoded.errorMessage)
        } finally { buf.release() }
    }

    @Test
    fun `ack packet round-trips failure with error message`() {
        val original = EditorPreviewAckS2CPacket(
            previewId = UUID.fromString("00000000-0000-0000-0000-000000000004"),
            ok = false,
            sceneId = null,
            errorMessage = Component.translatable("cobblemon.cinematic_editor.preview.validation_failed", 1, 1,
                Component.translatable("cobblemon.cinematic_editor.validation.detail", "beats[main]",
                    Component.translatable("cobblemon.cinematic_editor.validation.actor_missing", "hero")))
        )
        val buf = newBuffer()
        try {
            original.encode(buf)
            val decoded = EditorPreviewAckS2CPacket.decode(buf)

            assertEquals(false, decoded.ok)
            assertNull(decoded.sceneId)
            assertEquals(original.errorMessage, decoded.errorMessage)
        } finally { buf.release() }
    }
}
