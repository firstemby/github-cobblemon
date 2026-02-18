/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.gui

import com.cobblemon.mod.common.api.gui.scripted.GuiMode
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * S2C packet to open a scripted GUI on the client.
 */
class ScriptedGuiOpenPacket(
    val sessionId: UUID,
    val guiId: ResourceLocation,
    val mode: GuiMode,
    val args: Map<String, String>,
    val blocksInput: Boolean,
    val exclusive: Boolean
) : NetworkPacket<ScriptedGuiOpenPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        buffer.writeIdentifier(guiId)
        buffer.writeEnumConstant(mode)
        buffer.writeMap(args, { buf, key -> buf.writeString(key) }, { buf, value -> buf.writeString(value) })
        buffer.writeBoolean(blocksInput)
        buffer.writeBoolean(exclusive)
    }

    companion object {
        val ID = cobblemonResource("scripted_gui_open")

        fun decode(buffer: RegistryFriendlyByteBuf): ScriptedGuiOpenPacket {
            val sessionId = buffer.readUUID()
            val guiId = buffer.readIdentifier()
            val mode = buffer.readEnumConstant(GuiMode::class.java)
            val args = buffer.readMap({ buf -> buf.readString() }, { buf -> buf.readString() })
            val blocksInput = buffer.readBoolean()
            val exclusive = buffer.readBoolean()
            return ScriptedGuiOpenPacket(sessionId, guiId, mode, args, blocksInput, exclusive)
        }
    }
}
