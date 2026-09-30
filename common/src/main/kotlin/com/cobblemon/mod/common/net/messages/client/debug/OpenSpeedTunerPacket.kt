/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Opens the speed tuner with every species' current speeds. Species behaviour lives in server data so the client
 * can't read it itself.
 */
class OpenSpeedTunerPacket(val entries: List<SpeedTunerEntry>) : NetworkPacket<OpenSpeedTunerPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(entries) { buf, entry -> entry.encode(buf) }
    }

    companion object {
        val ID = cobblemonResource("open_speed_tuner")

        fun decode(buffer: RegistryFriendlyByteBuf): OpenSpeedTunerPacket {
            return OpenSpeedTunerPacket(buffer.readList { buf -> SpeedTunerEntry.decode(buf) })
        }
    }
}

/** A species' raw walk speed and its sprint speed in blocks per second, null if it can't sprint. */
data class SpeedTunerEntry(
    val speciesId: ResourceLocation,
    val dex: Int,
    val walkSpeed: Float,
    val sprintSpeed: Float?
) {
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeResourceLocation(speciesId)
        buffer.writeVarInt(dex)
        buffer.writeFloat(walkSpeed)
        buffer.writeBoolean(sprintSpeed != null)
        sprintSpeed?.let { buffer.writeFloat(it) }
    }

    companion object {
        fun decode(buffer: FriendlyByteBuf): SpeedTunerEntry {
            val speciesId = buffer.readResourceLocation()
            val dex = buffer.readVarInt()
            val walkSpeed = buffer.readFloat()
            val sprintSpeed = if (buffer.readBoolean()) buffer.readFloat() else null
            return SpeedTunerEntry(speciesId, dex, walkSpeed, sprintSpeed)
        }
    }
}
