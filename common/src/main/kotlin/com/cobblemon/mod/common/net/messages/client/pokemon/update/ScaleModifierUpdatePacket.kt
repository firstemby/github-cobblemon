/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Update packet for a Pokémon's scale modifier.
 *
 * @author Hiroku
 * @since December 1st, 2025
 */
class ScaleModifierUpdatePacket(pokemon: () -> Pokemon?, value: Float): SingleUpdatePacket<Float, ScaleModifierUpdatePacket>(pokemon, value) {
    override val id = ID
    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        buffer.writeFloat(this.value)
    }

    override fun set(pokemon: Pokemon, value: Float) {
        pokemon.scaleModifier = value
    }

    companion object {
        val ID = cobblemonResource("scale_modifier_update")
        fun decode(buffer: RegistryFriendlyByteBuf): ScaleModifierUpdatePacket {
            val pokemon = decodePokemon(buffer)
            val scaleModifier = buffer.readFloat()
            return ScaleModifierUpdatePacket(pokemon, scaleModifier)
        }
    }
}
