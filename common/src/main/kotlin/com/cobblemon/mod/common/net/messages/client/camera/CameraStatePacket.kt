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
import java.util.UUID

/**
 * Camera state and settings packet.
 *
 * Sent when camera state flags or settings change (not every tick).
 * Contains boolean flags and configuration that changes infrequently.
 */
class CameraStatePacket(
    val cameraId: UUID,
    val instantTransition: Boolean,
    val renderPlayerBody: Boolean,
    val hiddenHudElements: Set<HudElement> = emptySet()
) : NetworkPacket<CameraStatePacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(cameraId)
        buffer.writeBoolean(instantTransition)
        buffer.writeBoolean(renderPlayerBody)
        buffer.writeVarInt(encodeBitfield(hiddenHudElements))
    }

    companion object {
        val ID = cobblemonResource("camera_state")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraStatePacket {
            val cameraId = buffer.readUUID()
            val instantTransition = buffer.readBoolean()
            val renderPlayerBody = buffer.readBoolean()
            val hiddenHudElements = decodeBitfield(buffer.readVarInt())

            return CameraStatePacket(cameraId, instantTransition, renderPlayerBody, hiddenHudElements)
        }

        fun encodeBitfield(elements: Set<HudElement>): Int {
            var bits = 0
            for (element in elements) {
                bits = bits or (1 shl element.ordinal)
            }
            return bits
        }

        fun decodeBitfield(bits: Int): Set<HudElement> {
            val elements = mutableSetOf<HudElement>()
            for (element in HudElement.entries) {
                if (bits and (1 shl element.ordinal) != 0) {
                    elements.add(element)
                }
            }
            return elements
        }
    }
}
