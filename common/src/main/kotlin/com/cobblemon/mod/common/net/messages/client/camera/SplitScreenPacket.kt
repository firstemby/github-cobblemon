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
import java.util.UUID

/**
 * S2C packet to enable or disable split-screen diagonal compositing.
 *
 * When enabled, the client renders Camera A (primary) and Camera B (secondary)
 * side by side with a diagonal split line.
 */
class SplitScreenPacket(
    val enabled: Boolean,
    val primaryCameraId: UUID = UUID(0, 0),
    val secondaryCameraId: UUID = UUID(0, 0),
    val splitAngleDegrees: Float = 30f,
    val gapWidth: Float = 2f
) : NetworkPacket<SplitScreenPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(enabled)
        if (enabled) {
            buffer.writeUUID(primaryCameraId)
            buffer.writeUUID(secondaryCameraId)
            buffer.writeFloat(splitAngleDegrees)
            buffer.writeFloat(gapWidth)
        }
    }

    companion object {
        val ID = cobblemonResource("split_screen")

        fun decode(buffer: RegistryFriendlyByteBuf): SplitScreenPacket {
            val enabled = buffer.readBoolean()
            return if (enabled) {
                SplitScreenPacket(
                    enabled = true,
                    primaryCameraId = buffer.readUUID(),
                    secondaryCameraId = buffer.readUUID(),
                    splitAngleDegrees = buffer.readFloat(),
                    gapWidth = buffer.readFloat()
                )
            } else {
                SplitScreenPacket(enabled = false)
            }
        }
    }
}
