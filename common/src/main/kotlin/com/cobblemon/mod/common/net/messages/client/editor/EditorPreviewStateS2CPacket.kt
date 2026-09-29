/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.editor

import com.cobblemon.mod.common.api.cinematic.runtime.PreviewPlayback
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import java.util.UUID

class EditorPreviewStateS2CPacket(
    val previewId: UUID,
    val revision: Long,
    val beatId: String,
    val time: Float,
    val paused: Boolean,
    val error: Component? = null,
    val actorBounds: Map<String, AABB> = emptyMap()
) : NetworkPacket<EditorPreviewStateS2CPacket> {
    init {
        require(time.isFinite() && time in 0f..PreviewPlayback.MAX_SECONDS)
        require(actorBounds.size <= MAX_ACTORS)
        require(actorBounds.values.all {
            it.minX.isFinite() && it.minY.isFinite() && it.minZ.isFinite() &&
                it.maxX.isFinite() && it.maxY.isFinite() && it.maxZ.isFinite()
        })
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(previewId)
        buffer.writeLong(revision)
        buffer.writeUtf(beatId, 256)
        buffer.writeFloat(time)
        buffer.writeBoolean(paused)
        buffer.writeBoolean(error != null)
        error?.let { buffer.writeText(it) }
        buffer.writeVarInt(actorBounds.size)
        actorBounds.forEach { (id, bounds) ->
            buffer.writeUtf(id, 256)
            buffer.writeDouble(bounds.minX); buffer.writeDouble(bounds.minY); buffer.writeDouble(bounds.minZ)
            buffer.writeDouble(bounds.maxX); buffer.writeDouble(bounds.maxY); buffer.writeDouble(bounds.maxZ)
        }
    }
    companion object {
        const val MAX_ACTORS = 256
        val ID = cobblemonResource("editor_preview_state")
        fun decode(buffer: RegistryFriendlyByteBuf) = EditorPreviewStateS2CPacket(
            buffer.readUUID(), buffer.readLong(), buffer.readUtf(256), buffer.readFloat(), buffer.readBoolean(), if (buffer.readBoolean()) buffer.readText() else null,
            buildMap {
                val count = buffer.readVarInt()
                require(count in 0..MAX_ACTORS)
                repeat(count) {
                    val id = buffer.readUtf(256)
                    val bounds = AABB(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                    require(put(id, bounds) == null)
                }
            })
    }
}
