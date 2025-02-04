package com.cobblemon.mod.common.net.messages.client.pokemon.update.form

import com.cobblemon.mod.common.net.messages.client.pokemon.update.SingleUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf

class AddFormPacket(pokemon: () -> Pokemon, value: FormDisplay) : SingleUpdatePacket<FormDisplay, AddFormPacket>(pokemon, value) {

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