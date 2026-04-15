/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.molangui

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.molangui.MolangUiClientManager
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiClosePacket
import net.minecraft.client.Minecraft

object MolangUiCloseHandler : ClientNetworkPacketHandler<MolangUiClosePacket> {
    override fun handle(packet: MolangUiClosePacket, client: Minecraft) {
        MolangUiClientManager.close(packet.sessionId)
    }
}
