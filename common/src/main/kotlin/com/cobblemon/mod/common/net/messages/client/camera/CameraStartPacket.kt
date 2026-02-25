/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.camera

import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Signals the client to start a camera session. */
class CameraStartPacket(
    val cameraId: UUID,
    val initialPosition: Vec3? = null,
    val hiddenHudElements: Set<HudElement> = HudElement.ALL,
    val hideHand: Boolean = true
) : NetworkPacket<CameraStartPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(cameraId)
        buffer.writeBoolean(initialPosition != null)
        initialPosition?.let {
            buffer.writeDouble(it.x)
            buffer.writeDouble(it.y)
            buffer.writeDouble(it.z)
        }
        buffer.writeVarInt(CameraStatePacket.encodeBitfield(hiddenHudElements))
        buffer.writeBoolean(hideHand)
    }

    companion object {
        val ID = cobblemonResource("camera_start")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraStartPacket {
            val id = buffer.readUUID()
            val hasPos = buffer.readBoolean()
            val pos = if (hasPos) Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) else null
            val hiddenHudElements = CameraStatePacket.decodeBitfield(buffer.readVarInt())
            val hideHand = buffer.readBoolean()
            return CameraStartPacket(id, pos, hiddenHudElements, hideHand)
        }
    }
}
