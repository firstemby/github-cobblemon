/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * High-frequency camera transform update packet.
 *
 * Sent every tick (~20/sec) to update camera position and rotation.
 * Kept minimal for bandwidth efficiency.
 *
 * This packet is sent frequently, so it only contains essential
 * transform data. Other camera properties (focus, state) are sent
 * via separate packets only when they change.
 */
class CameraTransformPacket(
    val cameraId: UUID,
    val position: Vec3,
    val rotation: CameraRotation?,
    val updateCenterChunk: Boolean
) : NetworkPacket<CameraTransformPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(cameraId)

        buffer.writeDouble(position.x)
        buffer.writeDouble(position.y)
        buffer.writeDouble(position.z)

        buffer.writeBoolean(rotation != null)
        rotation?.let {
            buffer.writeFloat(it.yaw)
            buffer.writeFloat(it.pitch)
        }

        buffer.writeBoolean(updateCenterChunk)
    }

    companion object {
        val ID = cobblemonResource("camera_transform")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraTransformPacket {
            val cameraId = buffer.readUUID()

            val position = Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
            )

            val hasRotation = buffer.readBoolean()
            val rotation = if (hasRotation) {
                CameraRotation(
                    buffer.readFloat(),
                    buffer.readFloat()
                )
            } else {
                null
            }

            val updateCenterChunk = buffer.readBoolean()

            return CameraTransformPacket(cameraId, position, rotation, updateCenterChunk)
        }
    }
}
