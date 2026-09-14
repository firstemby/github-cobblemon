/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorRouteResultS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorRouteRequestC2SPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WaypointRoutePacketTest {
    @Test fun `route request round trips actor and authored track`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val packet = EditorRouteRequestC2SPacket(UUID.randomUUID(), "{}", SerializedBinding.Pokemon("hero", "pikachu", emptyList()))
            packet.encode(buffer)
            val copy = EditorRouteRequestC2SPacket.decode(buffer)
            assertEquals(packet.requestId, copy.requestId)
            assertEquals(packet.binding, copy.binding)
            assertEquals(packet.trackJson, copy.trackJson)
        } finally { buffer.release() }
    }

    @Test fun `partial results preserve trajectory search nodes and projected target`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val packet = EditorRouteResultS2CPacket(UUID.randomUUID(), listOf(WaypointRouteFrame(Vec3.ZERO, 35f, PoseType.WALK, false)),
                0.05f, false, 3, 1, Component.translatable("cobblemon.cinematic_editor.route.waypoint_error", 3, Component.translatable("cobblemon.cinematic_editor.route.no_route")), listOf(Vec3(1.0, 2.0, 3.0)), Vec3(5.0, 6.0, 7.0))
            packet.encode(buffer)
            val copy = EditorRouteResultS2CPacket.decode(buffer)
            assertEquals(packet.frames, copy.frames)
            assertEquals(packet.searchNodes, copy.searchNodes)
            assertEquals(packet.target, copy.target)
            assertEquals(packet.error, copy.error)
            assertEquals(packet.waypoint, copy.waypoint)
            assertEquals(packet.jumps, copy.jumps)
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }

    @Test fun `oversized frame counts are rejected before allocation`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeFloat(0f); buffer.writeBoolean(false)
            buffer.writeVarInt(1); buffer.writeVarInt(0); buffer.writeBoolean(false)
            buffer.writeVarInt(EditorRouteResultS2CPacket.MAX_FRAMES + 1)
            assertThrows(IllegalArgumentException::class.java) { EditorRouteResultS2CPacket.decode(buffer) }
        } finally { buffer.release() }
    }

    @Test fun `nonfinite coordinates are rejected`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            EditorRouteResultS2CPacket(UUID.randomUUID(), listOf(WaypointRouteFrame(Vec3(Double.NaN, 0.0, 0.0), 0f, PoseType.STAND, true))).encode(buffer)
            assertThrows(IllegalArgumentException::class.java) { EditorRouteResultS2CPacket.decode(buffer) }
        } finally { buffer.release() }
    }
}
