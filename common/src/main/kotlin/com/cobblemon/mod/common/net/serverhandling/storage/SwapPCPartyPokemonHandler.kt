/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.storage

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.net.messages.client.storage.pc.ClosePCPacket
import com.cobblemon.mod.common.net.messages.server.storage.SwapPCPartyPokemonPacket
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.util.setShoulderEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SwapPCPartyPokemonHandler : ServerNetworkPacketHandler<SwapPCPartyPokemonPacket> {
    override fun handle(packet: SwapPCPartyPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        val party = Cobblemon.storage.getParty(player)
        val pc = PCLinkManager.getPC(player) ?: return run { ClosePCPacket(null).sendToPlayer(player) }
        val partyPokemon = party[packet.partyPosition] ?: return
        val pcPokemon = pc[packet.pcPosition] ?: return

        if (partyPokemon.uuid != packet.partyPokemonID || pcPokemon.uuid != packet.pcPokemonID) {
            return
        }
        val shoulderedState = partyPokemon.state as? ShoulderedState
        party[packet.partyPosition] = pcPokemon
        shoulderedState
            ?.takeIf { it.playerUUID == player.uuid }
            ?.let { player.setShoulderEntity(it.isLeftShoulder, CompoundTag()) }
        pc[packet.pcPosition] = partyPokemon
    }
}
