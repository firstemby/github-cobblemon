package com.cobblemon.mod.common.net.messages.server.pokerod

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class CastPokeRodPacket(val mainHand: Boolean) : NetworkPacket<CastPokeRodPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(mainHand)
    }

    companion object {
        val ID = cobblemonResource("cast_pokerod")
        fun decode(buffer: RegistryFriendlyByteBuf) = CastPokeRodPacket(buffer.readBoolean())
    }
}