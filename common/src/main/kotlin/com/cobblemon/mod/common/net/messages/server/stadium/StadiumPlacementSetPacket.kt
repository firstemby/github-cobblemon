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
import net.minecraft.world.phys.Vec3

class StadiumPlacementSetPacket(
    val stadiumBlockPos: BlockPos,
    val layoutType: String,
    val sideIndex: Int,
    val slotType: String,
    val slotIndex: Int,
    val placedPosition: Vec3
) : NetworkPacket<StadiumPlacementSetPacket> {
    companion object {
        val ID = cobblemonResource("stadium_placement_set")

        fun decode(buffer: RegistryFriendlyByteBuf): StadiumPlacementSetPacket {
            val blockPos = buffer.readBlockPos()
            val layoutType = buffer.readUtf()
            val sideIndex = buffer.readVarInt()
            val slotType = buffer.readUtf()
            val slotIndex = buffer.readVarInt()
            val x = buffer.readDouble()
            val y = buffer.readDouble()
            val z = buffer.readDouble()
            return StadiumPlacementSetPacket(blockPos, layoutType, sideIndex, slotType, slotIndex, Vec3(x, y, z))
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(stadiumBlockPos)
        buffer.writeUtf(layoutType)
        buffer.writeVarInt(sideIndex)
        buffer.writeUtf(slotType)
        buffer.writeVarInt(slotIndex)
        buffer.writeDouble(placedPosition.x)
        buffer.writeDouble(placedPosition.y)
        buffer.writeDouble(placedPosition.z)
    }
}
