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
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SynchronizeActiveTMPacket(val tm: TechnicalMachine?): NetworkPacket<SynchronizeActiveTMPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(tm?.id) { pb, value -> pb.writeResourceLocation(value) }
    }

    companion object {
        val ID = cobblemonResource("synchronize_active_tm")
        fun decode(buffer: RegistryFriendlyByteBuf): SynchronizeActiveTMPacket {
            val resourceLocation = buffer.readNullable { it.readResourceLocation() }
            if (resourceLocation == null) return SynchronizeActiveTMPacket(null)
            val tm = TechnicalMachines.tmMap[resourceLocation]
            return SynchronizeActiveTMPacket(tm)
        }
    }
}