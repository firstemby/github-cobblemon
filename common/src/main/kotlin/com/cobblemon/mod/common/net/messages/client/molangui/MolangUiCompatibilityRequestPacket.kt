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
import net.minecraft.resources.ResourceLocation

class MolangUiCompatibilityRequestPacket(val contracts: Map<ResourceLocation, String>) : NetworkPacket<MolangUiCompatibilityRequestPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(contracts.size <= MolangUiPacketLimits.MAX_CONTRACTS)
        buffer.writeVarInt(contracts.size)
        contracts.forEach { (id, version) ->
            buffer.writeUtf(id.toString(), MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            buffer.writeUtf(version, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
        }
    }
    companion object {
        val ID = cobblemonResource("molang_ui_compatibility_request")
        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiCompatibilityRequestPacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            val count = buffer.readVarInt()
            require(count in 0..MolangUiPacketLimits.MAX_CONTRACTS)
            val contracts = linkedMapOf<ResourceLocation, String>()
            repeat(count) {
                val id = ResourceLocation.tryParse(buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH))
                    ?: throw IllegalArgumentException("invalid Molang UI id")
                contracts[id] = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            }
            return MolangUiCompatibilityRequestPacket(contracts)
        }
    }
}
