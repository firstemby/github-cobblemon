/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.trade

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.UnsplittablePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Packet sent to the client when the other player updates their offered Pokémon.
 *
 * Handled by [com.cobblemon.mod.common.client.net.trade.TradeUpdatedHandler]
 *
 * @author Hiroku
 * @since March 5th, 2023
 */
class TradeUpdatedPacket(val playerId: UUID, val pokemons: List<Pokemon>) : NetworkPacket<TradeUpdatedPacket>, UnsplittablePacket {
    companion object {
        val ID = cobblemonResource("trade_updated")
        fun decode(buffer: RegistryFriendlyByteBuf) = TradeUpdatedPacket(
            buffer.readUUID(),
            buffer.readList { Pokemon.S2C_CODEC.decode(it as RegistryFriendlyByteBuf) }
        )
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerId)
        buffer.writeCollection(pokemons) { buf, pokemon -> Pokemon.S2C_CODEC.encode(buf as RegistryFriendlyByteBuf, pokemon) }
    }
}