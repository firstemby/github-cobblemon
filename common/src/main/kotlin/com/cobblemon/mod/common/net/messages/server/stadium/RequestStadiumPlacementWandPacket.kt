/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestStadiumPlacementWandPacket(
    val blockPos: BlockPos,
    val layoutType: String,
    val sideIndex: Int,
    val slotType: String,
    val slotIndex: Int
) : NetworkPacket<RequestStadiumPlacementWandPacket> {
    companion object {
        val ID = cobblemonResource("request_stadium_placement_wand")

        fun decode(buffer: RegistryFriendlyByteBuf): RequestStadiumPlacementWandPacket {
            val blockPos = buffer.readBlockPos()
            val layoutType = buffer.readUtf()
            val sideIndex = buffer.readVarInt()
            val slotType = buffer.readUtf()
            val slotIndex = buffer.readVarInt()
            return RequestStadiumPlacementWandPacket(blockPos, layoutType, sideIndex, slotType, slotIndex)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeUtf(layoutType)
        buffer.writeVarInt(sideIndex)
        buffer.writeUtf(slotType)
        buffer.writeVarInt(slotIndex)
    }
}
