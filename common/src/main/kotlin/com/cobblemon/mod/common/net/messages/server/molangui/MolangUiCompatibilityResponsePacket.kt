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
import net.minecraft.resources.ResourceLocation

class MolangUiCompatibilityResponsePacket(val views: Map<ResourceLocation, String>) : NetworkPacket<MolangUiCompatibilityResponsePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(views.size <= MolangUiPacketLimits.MAX_CONTRACTS)
        buffer.writeVarInt(views.size)
        views.forEach { (id, version) ->
            buffer.writeUtf(id.toString(), MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            buffer.writeUtf(version, MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
        }
    }
    companion object {
        val ID = cobblemonResource("molang_ui_compatibility_response")
        fun decode(buffer: RegistryFriendlyByteBuf): MolangUiCompatibilityResponsePacket {
            require(buffer.readableBytes() <= MolangUiPacketLimits.MAX_PACKET_BYTES)
            val count = buffer.readVarInt()
            require(count in 0..MolangUiPacketLimits.MAX_CONTRACTS)
            val views = linkedMapOf<ResourceLocation, String>()
            repeat(count) {
                val id = ResourceLocation.tryParse(buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH))
                    ?: throw IllegalArgumentException("invalid Molang UI id")
                views[id] = buffer.readUtf(MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH)
            }
            return MolangUiCompatibilityResponsePacket(views)
        }
    }
}
