/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.editor

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewControlC2SPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object EditorPreviewControlHandler : ServerNetworkPacketHandler<EditorPreviewControlC2SPacket> {
    override fun handle(packet: EditorPreviewControlC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!player.hasPermissions(2)) return
        EditorPreviewSessionRegistry.enqueue(packet, player.uuid)
    }
}
