package com.cobblemon.mod.common.net.messages.server.pokemon.update.form

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

class AcceptFormPacket(val pokemonUUID: UUID, val formId: String) : NetworkPacket<AcceptFormPacket> {

    constructor(pokemon: Pokemon, form: FormDisplay) : this(pokemon.uuid, form.id)

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(this.pokemonUUID)
        buffer.writeString(this.formId)
    }

    companion object {
        val ID = cobblemonResource("accept_form")
        fun decode(buffer: RegistryFriendlyByteBuf) = AcceptFormPacket(buffer.readUUID(), buffer.readString())
    }
}