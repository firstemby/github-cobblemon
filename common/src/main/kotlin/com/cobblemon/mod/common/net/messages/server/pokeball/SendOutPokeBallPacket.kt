package com.cobblemon.mod.common.net.messages.server.pokeball

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SendOutPokeBallPacket() : NetworkPacket<SendOutPokeBallPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }

    companion object {
        val ID = cobblemonResource("send_out_pokeball")
        fun decode(buffer: RegistryFriendlyByteBuf) = SendOutPokeBallPacket()
    }
}