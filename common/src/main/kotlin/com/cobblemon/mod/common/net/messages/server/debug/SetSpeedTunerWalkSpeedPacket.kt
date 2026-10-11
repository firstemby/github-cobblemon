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

/** Sets a species' walkSpeed, in the species data's own units, on the running server. */
class SetSpeedTunerWalkSpeedPacket(val speciesId: ResourceLocation, val walkSpeed: Float) : NetworkPacket<SetSpeedTunerWalkSpeedPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(speciesId)
        buffer.writeFloat(walkSpeed)
    }

    companion object {
        val ID = cobblemonResource("set_speed_tuner_walk_speed")

        fun decode(buffer: RegistryFriendlyByteBuf): SetSpeedTunerWalkSpeedPacket {
            return SetSpeedTunerWalkSpeedPacket(buffer.readResourceLocation(), buffer.readFloat())
        }
    }
}
