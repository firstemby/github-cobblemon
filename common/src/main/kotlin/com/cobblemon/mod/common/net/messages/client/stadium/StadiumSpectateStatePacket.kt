package com.cobblemon.mod.common.net.messages.client.stadium

import com.cobblemon.mod.common.util.writeText
import com.cobblemon.mod.common.util.readText
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

data class StadiumSpectateStatePacket(val sessionId: UUID?, val name: Component = Component.empty(), val watching: Boolean = false) : NetworkPacket<StadiumSpectateStatePacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(sessionId != null)
        sessionId?.let { buffer.writeUUID(it) }
        buffer.writeText(name)
        buffer.writeBoolean(watching)
    }

    companion object {
        val ID = cobblemonResource("stadium_spectate_state")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumSpectateStatePacket(
            if (buffer.readBoolean()) buffer.readUUID() else null, buffer.readText(), buffer.readBoolean())
    }
}
