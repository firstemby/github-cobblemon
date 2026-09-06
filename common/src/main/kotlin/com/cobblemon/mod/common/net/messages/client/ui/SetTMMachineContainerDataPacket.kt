/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.client.net.gui.SetTMMachineContainerDataPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Tells the server to set container data within [TMMachineBlockEntity]
 *
 * Handled by [SetTMMachineContainerDataPacketHandler]
 */
class SetTMMachineContainerDataPacket(val dataSlotIndex: Int, val value: Int): NetworkPacket<SetTMMachineContainerDataPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(dataSlotIndex)
        buffer.writeInt(value)
    }

    companion object {
        val ID = cobblemonResource("tm_machine_set_container_data")

        fun decode(buffer: RegistryFriendlyByteBuf) = SetTMMachineContainerDataPacket(
            buffer.readInt(),
            buffer.readInt()
        )
    }
}
