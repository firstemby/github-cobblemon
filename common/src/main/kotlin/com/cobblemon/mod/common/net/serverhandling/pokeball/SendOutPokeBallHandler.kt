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
import com.cobblemon.mod.common.net.messages.server.pokeball.SendOutPokeBallPacket
import com.cobblemon.mod.common.util.isServerSide
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SendOutPokeBallHandler : ServerNetworkPacketHandler<SendOutPokeBallPacket> {

    override fun handle(packet: SendOutPokeBallPacket, server: MinecraftServer, player: ServerPlayer) {
        (player.mainHandItem.item as? PokeBallItem)?.let { pokeball ->
            if (player.level().isServerSide()) {
                pokeball.throwPokeBall(player.level(), player)
            }
            // TODO: player.mainHandItem.consume(1, player)
        }

    }
}
