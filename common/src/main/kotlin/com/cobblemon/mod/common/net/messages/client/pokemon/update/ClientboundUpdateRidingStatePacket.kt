/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class ClientboundUpdateRidingStatePacket(
    val entity: Int,
    val behaviour: ResourceLocation,
    val state: RidingBehaviourState? = null,
    val data: FriendlyByteBuf? = null
) : NetworkPacket<ClientboundUpdateRidingStatePacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        //We need to allow for encoding the data buffer instead of state for Flashback compat
        //Flashback needs to be able to call encode on the packet it gets, which only has data, not state yet.
        if (state == null && data == null) error("Expected state or data to be populated for encoding")
        buffer.writeInt(entity)
        buffer.writeResourceLocation(behaviour)
        if (state != null) {
            state.encode(buffer)
        }
        else if (data != null) {
            buffer.writeBytes(data)
            //So the buffer can be read in the future
            data.resetReaderIndex()
        }
    }

    companion object {
        val ID = cobblemonResource("s2c_update_ride_controller")
        fun decode(buffer: RegistryFriendlyByteBuf): ClientboundUpdateRidingStatePacket {
            val entity = buffer.readInt()
            val behaviour = buffer.readResourceLocation()
            val state = FriendlyByteBuf(buffer.readBytes(buffer.readableBytes()))
            return ClientboundUpdateRidingStatePacket(
                entity = entity,
                behaviour = behaviour,
                data = state
            )
        }
    }
}
