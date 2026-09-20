/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update.form

import com.cobblemon.mod.common.net.messages.client.PokemonUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ClearFormsPacket(pokemon: () -> Pokemon?) : PokemonUpdatePacket<ClearFormsPacket>(pokemon) {

    override val id = ID

    override fun encodeDetails(buffer: RegistryFriendlyByteBuf) {}

    override fun applyToPokemon() {
        (this.pokemon() ?: return).formProxy.client().clear()
    }

    companion object {
        val ID = cobblemonResource("clear_forms")
        fun decode(buffer: RegistryFriendlyByteBuf) = ClearFormsPacket(decodePokemon(buffer))
    }

}