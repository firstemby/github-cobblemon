/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.battle

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.TeamPreviewManager
import com.cobblemon.mod.common.net.messages.server.battle.TeamPreviewSelectPokemonPacket
import com.cobblemon.mod.common.util.safeParty
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object TeamPreviewSelectPokemonHandler : ServerNetworkPacketHandler<TeamPreviewSelectPokemonPacket> {
    override fun handle(packet: TeamPreviewSelectPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        val party = player.safeParty()?.toGappyList() ?: return
        for (slot in packet.selected) {
            if (slot < 0 || slot >= party.size) return
            if (party[slot] == null) return
        }
        TeamPreviewManager.changeSelections(player.uuid, packet.selected)
    }
}