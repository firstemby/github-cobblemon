/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.molangui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * C2S packet for player actions in a Molang UI (button clicks, etc).
 */
class MolangUiActionPacket(
    val sessionId: UUID,
    val action: String,
    val data: Map<String, String>
) : NetworkPacket<MolangUiActionPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        // Limit action string length to prevent abuse
        buffer.writeString(action.take(256))
        buffer.writeMap(data, { buf, key -> buf.writeString(key) }, { buf, value -> buf.writeString(value) })
    }

    companion object {
        val ID = cobblemonResource("molang_ui_action")

        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiActionPacket {
            val sessionId = buffer.readUUID()
            val action = buffer.readString()
            val data = buffer.readMap({ buf -> buf.readString() }, { buf -> buf.readString() })
            return MolangUiActionPacket(sessionId, action, data)
        }
    }
}
