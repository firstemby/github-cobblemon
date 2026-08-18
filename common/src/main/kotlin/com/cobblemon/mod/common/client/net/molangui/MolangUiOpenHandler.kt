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
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiOpenPacket
import net.minecraft.client.Minecraft

object MolangUiOpenHandler : ClientNetworkPacketHandler<MolangUiOpenPacket> {
    override fun handle(packet: MolangUiOpenPacket, client: Minecraft) {
        MolangUiClientManager.open(packet.sessionId, packet.guiId, packet.contractVersion, packet.mode, packet.args, packet.blocksInput, packet.exclusive, packet.hasContainer, packet.slotLayouts)
    }
}
