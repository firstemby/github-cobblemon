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
import java.util.UUID

class UpdateStadiumBlockPacket(
    val blockPos: BlockPos,
    val data: CompoundTag,
    val action: Action = Action.SAVE,
    val participantA: String = "",
    val participantB: String = "",
    val wand: RequestStadiumPlacementWandPacket? = null,
    val requestId: UUID
) : NetworkPacket<UpdateStadiumBlockPacket> {
    enum class Action { SAVE, START, WAND }
    companion object {
        val ID = cobblemonResource("update_stadium_block")

        fun decode(buffer: RegistryFriendlyByteBuf): UpdateStadiumBlockPacket {
            val blockPos = buffer.readBlockPos()
            val data = buffer.readNbt() ?: CompoundTag()
            val action = buffer.readEnum(Action::class.java)
            val participantA = buffer.readUtf(64)
            val participantB = buffer.readUtf(64)
            val wand = if (buffer.readBoolean()) RequestStadiumPlacementWandPacket.decode(buffer) else null
            return UpdateStadiumBlockPacket(blockPos, data, action, participantA, participantB, wand, buffer.readUUID())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeNbt(data)
        buffer.writeEnum(action)
        buffer.writeUtf(participantA, 64)
        buffer.writeUtf(participantB, 64)
        buffer.writeBoolean(wand != null)
        wand?.encode(buffer)
        buffer.writeUUID(requestId)
    }
}
