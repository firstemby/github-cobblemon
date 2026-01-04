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
 * @author Terrainwax
 * @since October 15th, 2025
 */
class CameraDebugPacket(
    val timelineId: String,
    val origin: Vec3
) : NetworkPacket<CameraDebugPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(timelineId)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
    }

    companion object {
        val ID = cobblemonResource("camera_debug")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraDebugPacket {
            val timelineId = buffer.readUtf()
            val x = buffer.readDouble()
            val y = buffer.readDouble()
            val z = buffer.readDouble()
            return CameraDebugPacket(timelineId, Vec3(x, y, z))
        }
    }
}

