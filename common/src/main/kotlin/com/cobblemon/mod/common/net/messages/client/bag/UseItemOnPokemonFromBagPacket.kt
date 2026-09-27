/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.bag

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class UseItemOnPokemonFromBagPacket(val partySlot: Int): NetworkPacket<UseItemOnPokemonFromBagPacket> {
    companion object {
        val ID = cobblemonResource("use_item_on_pokemon_from_bag")
        fun decode(buffer: RegistryFriendlyByteBuf): UseItemOnPokemonFromBagPacket {
            return UseItemOnPokemonFromBagPacket(buffer.readInt())
        }
    }
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(partySlot)
    }
}