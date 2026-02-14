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
import net.minecraft.resources.ResourceLocation

class UpdateHabitatBlockPacket(
    val habitatPos: BlockPos,
    val mimicId: ResourceLocation,
) : NetworkPacket<UpdateHabitatBlockPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(habitatPos)
        buffer.writeResourceLocation(mimicId)
    }

    companion object {
        val ID = cobblemonResource("c2s_update_habitat_block")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateHabitatBlockPacket {
            val habitatPos = buffer.readBlockPos()
            val mimicId = buffer.readResourceLocation()
            return UpdateHabitatBlockPacket(
                habitatPos = habitatPos,
                mimicId = mimicId,
            )
        }
    }
}