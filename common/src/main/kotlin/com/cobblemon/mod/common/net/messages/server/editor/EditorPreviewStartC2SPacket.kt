/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.editor

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

class EditorPreviewStartC2SPacket(
    val previewId: UUID,
    val sceneJson: String,
    val origin: Vec3,
    val bindings: List<SerializedBinding>,
    val beatId: String = "",
    val time: Float = 0f,
    val paused: Boolean = false
) : NetworkPacket<EditorPreviewStartC2SPacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(previewId)
        buffer.writeUtf(sceneJson, MAX_SCENE_JSON_CHARS)
        buffer.writeDouble(origin.x); buffer.writeDouble(origin.y); buffer.writeDouble(origin.z)
        buffer.writeVarInt(bindings.size)
        bindings.forEach { SerializedBinding.encode(buffer, it) }
        buffer.writeUtf(beatId, 256)
        buffer.writeFloat(time)
        buffer.writeBoolean(paused)
    }

    companion object {
        val ID: ResourceLocation = cobblemonResource("editor_preview_start")

        const val MAX_SCENE_JSON_CHARS = 1024 * 1024

        const val MAX_BINDINGS = 256

        // Decode precedes permission checks, so bound every count before allocating on the Netty thread.
        fun decode(buffer: RegistryFriendlyByteBuf): EditorPreviewStartC2SPacket {
            val previewId = buffer.readUUID()
            val sceneJson = buffer.readUtf(MAX_SCENE_JSON_CHARS)
            val origin = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
            val count = buffer.readVarInt()
            require(count in 0..MAX_BINDINGS) { "binding count $count exceeds $MAX_BINDINGS" }
            val bindings = List(count) { SerializedBinding.decode(buffer) }
            return EditorPreviewStartC2SPacket(previewId, sceneJson, origin, bindings,
                buffer.readUtf(256), buffer.readFloat(), buffer.readBoolean())
        }
    }
}
