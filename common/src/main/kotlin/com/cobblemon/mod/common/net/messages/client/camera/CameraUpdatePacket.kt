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

/** Updates camera position and focus on the client. */
class CameraUpdatePacket(
    val cameraId: UUID,
    val position: Vec3?,
    val focusPosition: Vec3?,
    val manualRotation: CameraRotation?,
    val updateCenterChunk: Boolean = false,
    val renderPlayerBody: Boolean = true
) : NetworkPacket<CameraUpdatePacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(cameraId)

        buffer.writeBoolean(position != null)
        position?.let {
            buffer.writeDouble(it.x)
            buffer.writeDouble(it.y)
            buffer.writeDouble(it.z)
        }

        buffer.writeBoolean(focusPosition != null)
        focusPosition?.let {
            buffer.writeDouble(it.x)
            buffer.writeDouble(it.y)
            buffer.writeDouble(it.z)
        }

        buffer.writeBoolean(manualRotation != null)
        manualRotation?.let {
            buffer.writeFloat(it.yaw)
            buffer.writeFloat(it.pitch)
        }

        buffer.writeBoolean(updateCenterChunk)
        buffer.writeBoolean(renderPlayerBody)
    }

    companion object {
        val ID = cobblemonResource("camera_update")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraUpdatePacket {
            val cameraId = buffer.readUUID()

            val hasPosition = buffer.readBoolean()
            val position = if (hasPosition) {
                Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
                )
            } else {
                null
            }

            val hasFocus = buffer.readBoolean()
            val focusPosition = if (hasFocus) {
                Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
                )
            } else {
                null
            }

            val hasManualRotation = buffer.readBoolean()
            val manualRotation = if (hasManualRotation) {
                CameraRotation(
                    buffer.readFloat(),
                    buffer.readFloat()
                )
            } else {
                null
            }

            val updateCenterChunk = buffer.readBoolean()
            val renderPlayerBody = buffer.readBoolean()

            return CameraUpdatePacket(cameraId, position, focusPosition, manualRotation, updateCenterChunk, renderPlayerBody)
        }
    }
}

