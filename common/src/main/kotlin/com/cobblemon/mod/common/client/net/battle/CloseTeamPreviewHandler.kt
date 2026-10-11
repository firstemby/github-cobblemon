/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.battle.preview.TeamPreviewGUI
import com.cobblemon.mod.common.net.messages.client.battle.CloseTeamPreviewPacket
import net.minecraft.client.Minecraft

object CloseTeamPreviewHandler : ClientNetworkPacketHandler<CloseTeamPreviewPacket> {
    override fun handle(packet: CloseTeamPreviewPacket, client: Minecraft) {
        if (client.screen is TeamPreviewGUI) {
            client.screen!!.onClose()
        }
    }
}