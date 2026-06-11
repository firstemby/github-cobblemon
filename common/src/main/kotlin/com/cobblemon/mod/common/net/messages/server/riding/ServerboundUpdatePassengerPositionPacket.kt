/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.riding

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3

class ServerboundUpdatePassengerPositionPacket(
    val passengerId: Int,
    val position: Vec3
) : NetworkPacket<ServerboundUpdatePassengerPositionPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(passengerId)
        buffer.writeDouble(position.x)
        buffer.writeDouble(position.y)
        buffer.writeDouble(position.z)
    }

    companion object {
        val ID = cobblemonResource("c2s_update_passenger_position")

        fun decode(buffer: RegistryFriendlyByteBuf): ServerboundUpdatePassengerPositionPacket {
            return ServerboundUpdatePassengerPositionPacket(
                buffer.readInt(),
                Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
            )
        }
    }
}
