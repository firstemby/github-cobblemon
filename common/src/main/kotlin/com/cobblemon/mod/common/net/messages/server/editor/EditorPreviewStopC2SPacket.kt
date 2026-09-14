/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.editor

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class EditorPreviewStopC2SPacket(val previewId: UUID) : NetworkPacket<EditorPreviewStopC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(previewId)
    }

    companion object {
        val ID: ResourceLocation = cobblemonResource("editor_preview_stop")
        fun decode(buffer: RegistryFriendlyByteBuf): EditorPreviewStopC2SPacket =
            EditorPreviewStopC2SPacket(buffer.readUUID())
    }
}
