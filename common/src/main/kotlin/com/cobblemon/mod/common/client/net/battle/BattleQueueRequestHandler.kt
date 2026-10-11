/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientRaidState
import com.cobblemon.mod.common.client.battle.SingleActionRequest
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket
import net.minecraft.client.Minecraft

object BattleQueueRequestHandler : ClientNetworkPacketHandler<BattleQueueRequestPacket> {
    override fun handle(packet: BattleQueueRequestPacket, client: Minecraft) {
        val battle = CobblemonClient.battle ?: return
        val playerUUID = Minecraft.getInstance().player?.uuid
        val actor = battle.sides.flatMap { it.actors }.find { it.uuid == playerUUID } ?: return
        val requests = SingleActionRequest.composeFrom(actor, packet.request)

        if (ClientRaidState.isInRaidBattle) {
            ClientRaidState.scheduleWithMoveDelay {
                CobblemonClient.battle?.pendingActionRequests = requests
            }
        } else {
            CobblemonClient.battle?.pendingActionRequests = requests
        }
    }
}