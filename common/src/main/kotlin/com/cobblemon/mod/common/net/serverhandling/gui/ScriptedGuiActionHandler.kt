/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.gui

import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiSessionManager
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.gui.ScriptedGuiActionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ScriptedGuiActionHandler : ServerNetworkPacketHandler<ScriptedGuiActionPacket> {
    override fun handle(packet: ScriptedGuiActionPacket, server: MinecraftServer, player: ServerPlayer) {
        ScriptedGuiSessionManager.handleAction(packet.sessionId, player, packet.action, packet.data)
    }
}
