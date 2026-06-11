/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.net.messages.client.pokemon.update.SingleUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Packet to let the client know what seat was chosen when starting a ride.
 *
 * @author Jackowes
 * @since March 31st, 2026
 */
class ClientboundSeatAssignmentPacket(
    val passengerEntityId: Int,
    val vehicleEntityId: Int,
    val seatLocator: String
) : NetworkPacket<ClientboundSeatAssignmentPacket> {
    override val id = ID

    companion object {
        val ID = cobblemonResource("seat_assignment")
        fun decode(buffer: RegistryFriendlyByteBuf) = ClientboundSeatAssignmentPacket(
            buffer.readInt(),
            buffer.readInt(),
            buffer.readString()
        )
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(passengerEntityId)
        buffer.writeInt(vehicleEntityId)
        buffer.writeString(seatLocator)
    }
}
