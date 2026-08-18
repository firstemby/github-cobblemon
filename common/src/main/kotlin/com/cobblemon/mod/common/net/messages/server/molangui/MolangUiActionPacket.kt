/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.net.messages.server.molangui

import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class MolangUiActionPacket(val sessionId: UUID, val action: String, val data: Map<String, String>) : NetworkPacket<MolangUiActionPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(data.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS)
        buffer.writeUUID(sessionId)
        buffer.writeUtf(action, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
        buffer.writeVarInt(data.size)
        data.forEach { (key, value) ->
            buffer.writeUtf(key, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            buffer.writeUtf(value, MolangUiPacketLimits.MAX_VALUE_LENGTH)
        }
    }
    companion object {
        val ID = cobblemonResource("molang_ui_action")
        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiActionPacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            val sessionId = buffer.readUUID()
            val action = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            val count = buffer.readVarInt()
            require(count in 0..MolangUiPacketLimits.MAX_ACTION_FIELDS)
            val data = linkedMapOf<String, String>()
            repeat(count) { data[buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)] = buffer.readUtf(MolangUiPacketLimits.MAX_VALUE_LENGTH) }
            return MolangUiActionPacket(sessionId, action, data)
        }
    }
}
