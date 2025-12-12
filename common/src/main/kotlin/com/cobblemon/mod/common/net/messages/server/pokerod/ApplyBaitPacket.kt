package com.cobblemon.mod.common.net.messages.server.pokerod

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ApplyBaitPacket() : NetworkPacket<ApplyBaitPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }

    companion object {
        val ID = cobblemonResource("apply_bait")
        fun decode(buffer: RegistryFriendlyByteBuf) = ApplyBaitPacket()
    }
}