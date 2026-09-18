/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.debug

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrainState
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Updates the client with information about whatever entity is being tracked for Bigger Brain debugging.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class UpdateTrackedBrainDataPacket(val entityId: Int, val state: BiggerBrainState?) : NetworkPacket<UpdateTrackedBrainDataPacket> {
    companion object {
        val ID = cobblemonResource("update_tracked_brain_data")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateTrackedBrainDataPacket {
            val entityId = buffer.readInt()
            val state = buffer.readNullable { BiggerBrainState.decode(buffer) }
            return UpdateTrackedBrainDataPacket(entityId, state)
        }
    }

    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entityId)
        buffer.writeNullable(state) { _, state-> state.encode(buffer) }
    }
}