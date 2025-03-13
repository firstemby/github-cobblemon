package com.cobblemon.mod.common.net.messages.server.pokemon.update.form

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

class RevertFormPacket(val pokemonUUID: UUID) : NetworkPacket<RevertFormPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(this.pokemonUUID)
    }

    companion object {
        val ID = cobblemonResource("revert_form")
        fun decode(buffer: RegistryFriendlyByteBuf) = RevertFormPacket(buffer.readUUID())
    }
}