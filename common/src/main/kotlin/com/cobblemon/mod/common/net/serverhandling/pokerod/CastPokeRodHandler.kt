/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokerod

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.net.messages.server.pokeball.SendOutPokeBallPacket
import com.cobblemon.mod.common.net.messages.server.pokerod.CastPokeRodPacket
import com.cobblemon.mod.common.util.isServerSide
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand

object CastPokeRodHandler : ServerNetworkPacketHandler<CastPokeRodPacket> {

    override fun handle(packet: CastPokeRodPacket, server: MinecraftServer, player: ServerPlayer) {
        val hand = if (packet.mainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        (player.getItemInHand(hand).item as? PokerodItem)?.let { pokerod ->
            if (player.level().isServerSide()) pokerod.attemptCast(
                player.level(),
                player,
                hand,
                player.getItemInHand(hand)
            )
        }

    }
}
