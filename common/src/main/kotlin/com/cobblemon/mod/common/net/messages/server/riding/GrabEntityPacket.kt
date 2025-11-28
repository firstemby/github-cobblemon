package com.cobblemon.mod.common.net.messages.server.riding

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class GrabEntityPacket : NetworkPacket<GrabEntityPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {}

    companion object {
        val ID = cobblemonResource("grab_entity")
        fun decode(buffer: RegistryFriendlyByteBuf) = GrabEntityPacket()
    }
}