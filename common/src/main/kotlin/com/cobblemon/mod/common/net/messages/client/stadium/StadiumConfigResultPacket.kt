/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.stadium

import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class StadiumConfigResultPacket(val blockPos: BlockPos, val requestId: UUID, val success: Boolean, val message: Component) : NetworkPacket<StadiumConfigResultPacket> {
    companion object {
        val ID = cobblemonResource("stadium_config_result")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumConfigResultPacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readBoolean(), buffer.readText())
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        buffer.writeUUID(requestId)
        buffer.writeBoolean(success)
        buffer.writeText(message)
    }
}
