/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokeball

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.net.messages.server.SendOutPokeballPacket
import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SendOutPokeballHandler : ServerNetworkPacketHandler<SendOutPokeballPacket> {

    override fun handle(packet: SendOutPokeballPacket, server: MinecraftServer, player: ServerPlayer) {
        (player.mainHandItem.item as? PokeBallItem)?.let { pokeball ->
            PokeBallItem.throwPokeBall(pokeball.pokeBall, player.level(), player)
        }

    }
}
