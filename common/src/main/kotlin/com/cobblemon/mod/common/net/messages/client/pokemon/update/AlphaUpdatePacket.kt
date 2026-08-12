/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Update packet for the alpha (as in the big scary pack leader) status of a Pokémon.
 *
 * @author Hiroku
 * @since December 1st, 2025
 */
class AlphaUpdatePacket(pokemon: () -> Pokemon?, value: Boolean): SingleUpdatePacket<Boolean, AlphaUpdatePacket>(pokemon, value) {
    override val id = ID
    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(this.value)
    }

    override fun set(pokemon: Pokemon, value: Boolean) {
        pokemon.isAlpha = value
    }

    companion object {
        val ID = cobblemonResource("alpha_update")
        fun decode(buffer: RegistryFriendlyByteBuf): AlphaUpdatePacket  {
            val pokemon = decodePokemon(buffer)
            val value = buffer.readBoolean()
            return AlphaUpdatePacket(pokemon, value)
        }
    }
}