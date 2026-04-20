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
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

class UpdateStadiumBlockPacket(val blockPos: BlockPos, val data: CompoundTag) : NetworkPacket<UpdateStadiumBlockPacket> {
    companion object {
        val ID = cobblemonResource("update_stadium_block")

        fun decode(buffer: RegistryFriendlyByteBuf): UpdateStadiumBlockPacket {
            val blockPos = buffer.readBlockPos()
            val data = buffer.readNbt() ?: CompoundTag()
            return UpdateStadiumBlockPacket(blockPos, data)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeNbt(data)
    }
}
