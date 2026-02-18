/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.gui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readUUID
import com.cobblemon.mod.common.util.writeUUID
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * S2C packet to close a scripted GUI on the client.
 */
class ScriptedGuiClosePacket(
    val sessionId: UUID
) : NetworkPacket<ScriptedGuiClosePacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
    }

    companion object {
        val ID = cobblemonResource("scripted_gui_close")

        fun decode(buffer: RegistryFriendlyByteBuf): ScriptedGuiClosePacket {
            return ScriptedGuiClosePacket(buffer.readUUID())
        }
    }
}
