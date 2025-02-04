package com.cobblemon.mod.common.net.messages.client.pokemon.update.form

import com.cobblemon.mod.common.net.messages.client.PokemonUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ClearFormsPacket(pokemon: () -> Pokemon) : PokemonUpdatePacket<ClearFormsPacket>(pokemon) {

    override val id = ID

    override fun encodeDetails(buffer: RegistryFriendlyByteBuf) {}

    override fun applyToPokemon() {
        this.pokemon().formProxy.client().clear()
    }

    companion object {
        val ID = cobblemonResource("clear_forms")
        fun decode(buffer: RegistryFriendlyByteBuf) = ClearFormsPacket(decodePokemon(buffer))
    }

}