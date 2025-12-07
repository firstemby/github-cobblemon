package com.cobblemon.mod.common.net.messages.server.pokerod

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ReelPokeRodPacket(val mainHand: Boolean) : NetworkPacket<ReelPokeRodPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(mainHand)
    }

    companion object {
        val ID = cobblemonResource("reel_pokerod")
        fun decode(buffer: RegistryFriendlyByteBuf) = ReelPokeRodPacket(buffer.readBoolean())
    }
}