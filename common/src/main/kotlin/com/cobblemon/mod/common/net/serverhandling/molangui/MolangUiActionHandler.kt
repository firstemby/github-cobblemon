/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.molangui

import com.cobblemon.mod.common.api.molangui.MolangUiService
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiActionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MolangUiActionHandler : ServerNetworkPacketHandler<MolangUiActionPacket> {
    override fun handle(packet: MolangUiActionPacket, server: MinecraftServer, player: ServerPlayer) {
        MolangUiService.handleAction(packet.sessionId, player, packet.action, packet.data)
    }
}
