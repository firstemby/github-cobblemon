/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class UpdateGrottoBlockPacket(
    val grottoPos: BlockPos
) : NetworkPacket<UpdateGrottoBlockPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(grottoPos)
    }

    companion object {
        val ID = cobblemonResource("c2s_update_grotto_block")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateGrottoBlockPacket =
            UpdateGrottoBlockPacket(
                grottoPos = buffer.readBlockPos(),
            )
    }
}