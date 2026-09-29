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
import java.util.UUID

class EditorRouteRequestC2SPacket(val requestId: UUID, val trackJson: String, val binding: SerializedBinding) :
    NetworkPacket<EditorRouteRequestC2SPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(requestId)
        buffer.writeUtf(trackJson, 32768)
        SerializedBinding.encode(buffer, binding)
    }
    companion object {
        val ID = cobblemonResource("editor_route_request")
        fun decode(buffer: RegistryFriendlyByteBuf) = EditorRouteRequestC2SPacket(
            buffer.readUUID(), buffer.readUtf(32768), SerializedBinding.decode(buffer))
    }
}
