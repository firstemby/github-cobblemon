/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.editor

import com.cobblemon.mod.common.util.writeText
import com.cobblemon.mod.common.util.readText
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointRouteFrame
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

class EditorRouteResultS2CPacket(
    val requestId: UUID,
    val frames: List<WaypointRouteFrame> = emptyList(),
    val seconds: Float = 0f,
    val finished: Boolean = false,
    val waypoint: Int = 1,
    val jumps: Int = 0,
    val error: Component? = null,
    val searchNodes: List<Vec3> = emptyList(),
    val target: Vec3? = null
) : NetworkPacket<EditorRouteResultS2CPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(requestId)
        buffer.writeFloat(seconds)
        buffer.writeBoolean(finished)
        buffer.writeVarInt(waypoint)
        buffer.writeVarInt(jumps)
        buffer.writeBoolean(error != null)
        error?.let { buffer.writeText(it) }
        require(frames.size <= MAX_FRAMES && searchNodes.size <= MAX_NODES)
        buffer.writeVarInt(frames.size)
        frames.forEach {
            writeVector(buffer, it.position)
            buffer.writeFloat(it.yaw)
            buffer.writeEnum(it.pose)
            buffer.writeBoolean(it.onGround)
        }
        buffer.writeVarInt(searchNodes.size)
        searchNodes.forEach { writeVector(buffer, it) }
        buffer.writeBoolean(target != null)
        target?.let { writeVector(buffer, it) }
    }
    companion object {
        const val MAX_FRAMES = 12001
        const val MAX_NODES = 4096
        val ID = cobblemonResource("editor_route_result")
        fun decode(buffer: RegistryFriendlyByteBuf): EditorRouteResultS2CPacket {
            val id = buffer.readUUID()
            val seconds = buffer.readFloat()
            require(seconds.isFinite() && seconds in 0f..600f)
            val finished = buffer.readBoolean()
            val waypoint = buffer.readVarInt()
            val jumps = buffer.readVarInt()
            val error = if (buffer.readBoolean()) buffer.readText() else null
            val count = buffer.readVarInt()
            require(count in 0..MAX_FRAMES)
            val frames = List(count) {
                val position = readVector(buffer)
                val yaw = buffer.readFloat()
                require(yaw.isFinite())
                WaypointRouteFrame(position, yaw, buffer.readEnum(PoseType::class.java), buffer.readBoolean())
            }
            val nodes = buffer.readVarInt()
            require(nodes in 0..MAX_NODES)
            return EditorRouteResultS2CPacket(id, frames, seconds, finished, waypoint, jumps, error,
                List(nodes) { readVector(buffer) }, if (buffer.readBoolean()) readVector(buffer) else null)
        }
        private fun writeVector(buffer: RegistryFriendlyByteBuf, point: Vec3) {
            buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z)
        }
        private fun readVector(buffer: RegistryFriendlyByteBuf): Vec3 {
            val point = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
            require(point.x.isFinite() && point.y.isFinite() && point.z.isFinite())
            return point
        }
    }
}
