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
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class FormUpdatePacket(pokemon: () -> Pokemon?, formName: String) : SingleUpdatePacket<String, FormUpdatePacket>(pokemon, formName) {
    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        buffer.writeString(this.value)
    }

    override fun set(pokemon: Pokemon, value: String) {
        val form = pokemon.species.forms.find { it.name == value } ?: pokemon.species.standardForm
        pokemon.form = form
    }

    override val id = ID

    companion object {
        val ID: ResourceLocation = cobblemonResource("form_update")

        fun decode(buffer: RegistryFriendlyByteBuf): FormUpdatePacket {
            val pokemon = decodePokemon(buffer)
            val formName = buffer.readString()
            return FormUpdatePacket(pokemon, formName)
        }
    }
}