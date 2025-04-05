package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.writeIdentifier
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.*

class StadiumJoinPacket(
    val joiningBattle: UUID,
    val cameraManager: ResourceLocation
): NetworkPacket<StadiumJoinPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(this.joiningBattle)
        buffer.writeIdentifier(cameraManager)
    }
    companion object {
        val ID = cobblemonResource("stadium_join")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumJoinPacket(buffer.readUUID(), buffer.readIdentifier())
    }
}