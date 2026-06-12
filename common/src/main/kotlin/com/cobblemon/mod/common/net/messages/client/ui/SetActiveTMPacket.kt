/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.block.tmmachine.TMMachineBlock
import com.cobblemon.mod.common.client.net.gui.SetActiveTMPacketHandler
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Tells the server to set active [TechnicalMachineItem] using the [TMMachineBlock]
 *
 * Handled by [SetActiveTMPacketHandler]
 */
class SetActiveTMPacket(val tm: TechnicalMachine?): NetworkPacket<SetActiveTMPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(tm) { _, v -> buffer.writeResourceLocation(v.id) }
    }

    companion object {
        val ID = cobblemonResource("set_active_tm")

        fun decode(buffer: RegistryFriendlyByteBuf) = SetActiveTMPacket(
            TechnicalMachines.tmMap[buffer.readNullable { buffer.readResourceLocation() }]
        )
    }
}
