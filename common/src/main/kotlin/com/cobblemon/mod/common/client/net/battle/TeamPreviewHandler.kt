/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.battle.preview.ClientBattleTeamPreview
import com.cobblemon.mod.common.client.gui.battle.preview.TeamPreviewGUI
import com.cobblemon.mod.common.net.messages.client.battle.TeamPreviewPacket
import net.minecraft.client.Minecraft

object TeamPreviewHandler : ClientNetworkPacketHandler<TeamPreviewPacket> {
    override fun handle(packet: TeamPreviewPacket, client: Minecraft) {
        Minecraft.getInstance().setScreen(TeamPreviewGUI(ClientBattleTeamPreview(
            packet.selections,
            packet.team,
            packet.opponent,
            packet.mustPickBy,
            packet.opponentName
        )))
    }
}