package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

class StadiumJoinPacket(val joiningPlayer: UUID): NetworkPacket<StadiumJoinPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(joiningPlayer)
    }
    companion object {
        val ID = cobblemonResource("stadium_join")
    }
}