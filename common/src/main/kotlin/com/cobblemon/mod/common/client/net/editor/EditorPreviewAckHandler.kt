/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.editor

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewAckS2CPacket
import net.minecraft.client.Minecraft

object EditorPreviewAckHandler : ClientNetworkPacketHandler<EditorPreviewAckS2CPacket> {
    override fun handle(packet: EditorPreviewAckS2CPacket, client: Minecraft) {
        Cobblemon.LOGGER.info(
            "[EditorPreview] ack received previewId=${packet.previewId} ok=${packet.ok} sceneId=${packet.sceneId} error=${packet.errorMessage}"
        )
        if (packet.ok && packet.sceneId != null) {
            SceneEditorOverlay.preview.onAckSuccess(packet.previewId, packet.sceneId)
        } else {
            Cobblemon.LOGGER.warn(
                "[EditorPreview] preview ${packet.previewId} failed: ${packet.errorMessage ?: "unknown error"}"
            )
            SceneEditorOverlay.preview.onAckFailure(packet.previewId, packet.errorMessage?.string ?: I18n.get("cobblemon.cinematic_editor.preview.start_failed"))
        }
    }
}
