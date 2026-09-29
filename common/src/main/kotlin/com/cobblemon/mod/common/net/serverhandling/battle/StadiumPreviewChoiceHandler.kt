/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.battle

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.net.messages.server.battle.StadiumPreviewChoicePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object StadiumPreviewChoiceHandler : ServerNetworkPacketHandler<StadiumPreviewChoicePacket> {
    override fun handle(packet: StadiumPreviewChoicePacket, server: MinecraftServer, player: ServerPlayer) {
        val battle = BattleRegistry.getBattle(packet.battleId) ?: return
        val actor = battle.actors.find { player.uuid in it.getPlayerUUIDs() } ?: return
        battle.stadiumSession?.submit(actor, packet.rqid, packet.order)
    }
}
