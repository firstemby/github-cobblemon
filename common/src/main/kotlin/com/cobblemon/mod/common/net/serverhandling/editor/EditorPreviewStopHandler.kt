/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.editor

import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStopC2SPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object EditorPreviewStopHandler : ServerNetworkPacketHandler<EditorPreviewStopC2SPacket> {
    override fun handle(packet: EditorPreviewStopC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val sceneId = EditorPreviewSessionRegistry.consume(packet.previewId, player.uuid) ?: return
        CinematicManager.stopScene(sceneId)
    }
}
