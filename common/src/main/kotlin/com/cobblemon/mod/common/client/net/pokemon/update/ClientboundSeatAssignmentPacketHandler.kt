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
        val passenger = client.level?.getEntity(packet.passengerEntityId) ?: return
        val seat = vehicle.form.riding.seats.firstOrNull { it.locator == packet.seatLocator } ?: return

        // If the passenger was already seated somewhere else then move seats for them if needed.
        // It could also be that the client assigned the wrong seat. The server has authority in seating so
        // replace in that case too.
        val currentSeat = vehicle.occupiedSeats.entries.firstOrNull { it.value == passenger }
        if (currentSeat != null) {
            if (currentSeat.key == seat) return
            vehicle.occupiedSeats.remove(currentSeat.key)
        }

        vehicle.occupiedSeats[seat] = passenger

        if (!vehicle.hasPassenger(passenger)) {
            passenger.startRiding(vehicle, true)
        }
    }
}
