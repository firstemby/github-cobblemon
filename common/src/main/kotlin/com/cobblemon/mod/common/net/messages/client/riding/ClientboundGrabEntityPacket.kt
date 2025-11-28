package com.cobblemon.mod.common.net.messages.client.riding

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ClientboundGrabEntityPacket internal constructor(
    val vehicle: Int,
    val entity: Int?
) : NetworkPacket<ClientboundGrabEntityPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(vehicle)
        buffer.writeNullable(entity) { _, v -> buffer.writeVarInt(v) }
    }

    companion object {
        val ID = cobblemonResource("clientbound_grab_entity")
        fun decode(buffer: RegistryFriendlyByteBuf): ClientboundGrabEntityPacket {
            val vehicle = buffer.readVarInt()
            val entity = buffer.readNullable { buffer.readVarInt() }
            return ClientboundGrabEntityPacket(vehicle, entity)
        }
    }
}
