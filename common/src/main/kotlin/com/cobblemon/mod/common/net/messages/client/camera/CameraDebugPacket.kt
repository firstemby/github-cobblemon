/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.camera

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3

/**
 * Packet sent to client to control camera debug visualization.
 * 
 * On dedicated servers, the client doesn't have access to SERVER_DATA timelines,
 * so this packet includes the timeline JSON data for the client to parse and use
 * for debug visualization.
 *
 * @param timelineId The timeline ID to visualize (empty string to clear)
 * @param origin The origin point for the camera visualization
 * @param timelineJson Optional JSON data for the timeline (for dedicated server support)
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
class CameraDebugPacket(
    val timelineId: String,
    val origin: Vec3,
    val timelineJson: String? = null
) : NetworkPacket<CameraDebugPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(timelineId)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
        buffer.writeBoolean(timelineJson != null)
        if (timelineJson != null) {
            buffer.writeUtf(timelineJson, MAX_JSON_LENGTH)
        }
    }

    companion object {
        val ID = cobblemonResource("camera_debug")
        
        // Max JSON length - timelines can be large with many states/tracks
        private const val MAX_JSON_LENGTH = 65535

        fun decode(buffer: RegistryFriendlyByteBuf): CameraDebugPacket {
            val timelineId = buffer.readUtf()
            val x = buffer.readDouble()
            val y = buffer.readDouble()
            val z = buffer.readDouble()
            val hasJson = buffer.readBoolean()
            val timelineJson = if (hasJson) buffer.readUtf(MAX_JSON_LENGTH) else null
            return CameraDebugPacket(timelineId, Vec3(x, y, z), timelineJson)
        }
    }
}

