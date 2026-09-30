/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Sets a species' sprint speed in blocks per second on the running server. Null takes sprinting away. */
class SetSpeedTunerSprintSpeedPacket(val speciesId: ResourceLocation, val sprintSpeed: Float?) : NetworkPacket<SetSpeedTunerSprintSpeedPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(speciesId)
        buffer.writeBoolean(sprintSpeed != null)
        sprintSpeed?.let { buffer.writeFloat(it) }
    }

    companion object {
        val ID = cobblemonResource("set_speed_tuner_sprint_speed")

        fun decode(buffer: RegistryFriendlyByteBuf): SetSpeedTunerSprintSpeedPacket {
            val speciesId = buffer.readResourceLocation()
            val sprintSpeed = if (buffer.readBoolean()) buffer.readFloat() else null
            return SetSpeedTunerSprintSpeedPacket(speciesId, sprintSpeed)
        }
    }
}
