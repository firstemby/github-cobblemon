/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.gui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * S2C packet to update arguments on a running scripted GUI.
 * Only changed arguments are sent (partial update).
 */
class ScriptedGuiUpdatePacket(
    val sessionId: UUID,
    val args: Map<String, String>
) : NetworkPacket<ScriptedGuiUpdatePacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        buffer.writeMap(args, { buf, key -> buf.writeString(key) }, { buf, value -> buf.writeString(value) })
    }

    companion object {
        val ID = cobblemonResource("scripted_gui_update")

        fun decode(buffer: RegistryFriendlyByteBuf): ScriptedGuiUpdatePacket {
            val sessionId = buffer.readUUID()
            val args = buffer.readMap({ buf -> buf.readString() }, { buf -> buf.readString() })
            return ScriptedGuiUpdatePacket(sessionId, args)
        }
    }
}
