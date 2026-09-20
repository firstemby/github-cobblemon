/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update.form

import com.cobblemon.mod.common.net.messages.client.pokemon.update.SingleUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class AddFormPacket(pokemon: () -> Pokemon?, value: FormDisplay) : SingleUpdatePacket<FormDisplay, AddFormPacket>(pokemon, value) {

    override val id = ID

    constructor(pokemon: Pokemon, value: TemporaryForm) : this({ pokemon }, value.convertToDisplay(pokemon))

    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        this.value.encode(buffer)
    }

    override fun set(pokemon: Pokemon, value: FormDisplay) {
        pokemon.formProxy.client().add(value)
    }

    companion object {

        val ID = cobblemonResource("add_form")

        fun decode(buffer: RegistryFriendlyByteBuf) = AddFormPacket(decodePokemon(buffer), FormDisplay.decode(buffer))
    }
}