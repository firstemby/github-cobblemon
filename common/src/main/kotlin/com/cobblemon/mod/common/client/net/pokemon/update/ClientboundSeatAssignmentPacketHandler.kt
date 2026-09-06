/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.pokemon.update

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.ClientboundSeatAssignmentPacket
import net.minecraft.client.Minecraft

object ClientboundSeatAssignmentPacketHandler : ClientNetworkPacketHandler<ClientboundSeatAssignmentPacket> {
    override fun handle(packet: ClientboundSeatAssignmentPacket, client: Minecraft) {
        val vehicle = client.level?.getEntity(packet.vehicleEntityId) as? PokemonEntity ?: return
        val assignments = packet.seatAssignments.mapNotNull { (locator, entityId) ->
            val seat = vehicle.form.riding.seats.firstOrNull { it.locator == locator } ?: return@mapNotNull null
            val passenger = client.level?.getEntity(entityId) ?: return@mapNotNull null
            seat to passenger
        }

        vehicle.occupiedSeats.clear()
        assignments.forEach { (seat, passenger) ->
            vehicle.occupiedSeats[seat] = passenger
        }
    }
}
