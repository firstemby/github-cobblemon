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
import java.util.UUID

class EditorPreviewControlC2SPacket(
    val previewId: UUID,
    val revision: Long,
    val beatId: String,
    val time: Float,
    val paused: Boolean
) : NetworkPacket<EditorPreviewControlC2SPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(previewId)
        buffer.writeLong(revision)
        buffer.writeUtf(beatId, 256)
        buffer.writeFloat(time)
        buffer.writeBoolean(paused)
    }
    companion object {
        val ID = cobblemonResource("editor_preview_control")
        fun decode(buffer: RegistryFriendlyByteBuf) = EditorPreviewControlC2SPacket(
            buffer.readUUID(), buffer.readLong(), buffer.readUtf(256), buffer.readFloat(), buffer.readBoolean())
    }
}
