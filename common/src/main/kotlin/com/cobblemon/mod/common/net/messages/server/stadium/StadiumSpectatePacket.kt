package com.cobblemon.mod.common.net.messages.server.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class StadiumSpectatePacket(val sessionId: UUID, val leave: Boolean) : NetworkPacket<StadiumSpectatePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sessionId)
        buffer.writeBoolean(leave)
    }
    companion object {
        val ID = cobblemonResource("stadium_spectate")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumSpectatePacket(buffer.readUUID(), buffer.readBoolean())
    }
}
