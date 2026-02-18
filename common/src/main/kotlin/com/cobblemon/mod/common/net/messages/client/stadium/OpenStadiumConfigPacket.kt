/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenStadiumConfigPacket(val blockPos: BlockPos, val data: CompoundTag) : NetworkPacket<OpenStadiumConfigPacket> {
    companion object {
        val ID = cobblemonResource("open_stadium_config")

        fun decode(buffer: RegistryFriendlyByteBuf): OpenStadiumConfigPacket {
            val blockPos = buffer.readBlockPos()
            val data = buffer.readNbt() ?: CompoundTag()
            return OpenStadiumConfigPacket(blockPos, data)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeNbt(data)
    }
}
