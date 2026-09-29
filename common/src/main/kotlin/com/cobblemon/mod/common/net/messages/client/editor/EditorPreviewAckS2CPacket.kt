/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.editor

import com.cobblemon.mod.common.util.writeText
import com.cobblemon.mod.common.util.readText
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class EditorPreviewAckS2CPacket(
    val previewId: UUID,
    val ok: Boolean,
    val sceneId: UUID?,
    val errorMessage: Component?
) : NetworkPacket<EditorPreviewAckS2CPacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(previewId)
        buffer.writeBoolean(ok)
        val hasSceneId = sceneId != null
        buffer.writeBoolean(hasSceneId)
        if (hasSceneId) buffer.writeUUID(sceneId!!)
        val hasError = errorMessage != null
        buffer.writeBoolean(hasError)
        if (hasError) buffer.writeText(errorMessage!!)
    }

    companion object {
        val ID: ResourceLocation = cobblemonResource("editor_preview_ack")

        fun decode(buffer: RegistryFriendlyByteBuf): EditorPreviewAckS2CPacket {
            val previewId = buffer.readUUID()
            val ok = buffer.readBoolean()
            val sceneId = if (buffer.readBoolean()) buffer.readUUID() else null
            val errorMessage = if (buffer.readBoolean()) buffer.readText() else null
            return EditorPreviewAckS2CPacket(previewId, ok, sceneId, errorMessage)
        }
    }
}
