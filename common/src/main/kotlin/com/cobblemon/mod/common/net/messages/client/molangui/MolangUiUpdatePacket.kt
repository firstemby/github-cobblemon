/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.molangui

import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class MolangUiUpdatePacket(val sessionId: UUID, val args: Map<String, String>, val variables: Map<String, Double> = emptyMap()) : NetworkPacket<MolangUiUpdatePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(args.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS && variables.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS)
        buffer.writeUUID(sessionId).writeVarInt(args.size)
        args.forEach { (key, value) -> buffer.writeUtf(key, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH).writeUtf(value, MolangUiPacketLimits.MAX_VALUE_LENGTH) }
        buffer.writeVarInt(variables.size)
        variables.forEach { (key, value) -> require(value.isFinite()); buffer.writeUtf(key, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH).writeDouble(value) }
    }
    companion object {
        val ID = cobblemonResource("molang_ui_update")
        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiUpdatePacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            val sessionId = buffer.readUUID()
            val argCount = buffer.readVarInt(); require(argCount in 0..MolangUiPacketLimits.MAX_ACTION_FIELDS)
            val args = linkedMapOf<String, String>()
            repeat(argCount) { args[buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)] = buffer.readUtf(MolangUiPacketLimits.MAX_VALUE_LENGTH) }
            val variableCount = buffer.readVarInt(); require(variableCount in 0..MolangUiPacketLimits.MAX_ACTION_FIELDS)
            val variables = linkedMapOf<String, Double>()
            repeat(variableCount) {
                val key = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
                val value = buffer.readDouble(); require(value.isFinite()); variables[key] = value
            }
            return MolangUiUpdatePacket(sessionId, args, variables)
        }
    }
}
