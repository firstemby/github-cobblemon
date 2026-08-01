/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.riding

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate
import com.cobblemon.mod.common.net.messages.server.riding.ServerboundUpdatePassengerPositionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ServerboundUpdatePassengerPositionHandler : ServerNetworkPacketHandler<ServerboundUpdatePassengerPositionPacket> {

    override fun handle(packet: ServerboundUpdatePassengerPositionPacket, server: MinecraftServer, player: ServerPlayer) {
        // Validate this player should be setting this position on this vehicle
        val vehicle = player.vehicle as? PokemonEntity ?: return
        if (packet.passengerId != player.id) return
        val delegate = vehicle.delegate as? PokemonServerDelegate ?: return
        delegate.updatePassengerPosition(packet.passengerId, packet.position)
    }
}
