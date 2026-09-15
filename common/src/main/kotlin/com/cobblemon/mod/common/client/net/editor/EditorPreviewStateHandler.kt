/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.editor

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.cinematic.CinematicClientExtensions
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewStateS2CPacket
import net.minecraft.client.Minecraft

object EditorPreviewStateHandler : ClientNetworkPacketHandler<EditorPreviewStateS2CPacket> {
    override fun handle(packet: EditorPreviewStateS2CPacket, client: Minecraft) {
        CinematicClientExtensions.previewState(packet, client)
    }
}
