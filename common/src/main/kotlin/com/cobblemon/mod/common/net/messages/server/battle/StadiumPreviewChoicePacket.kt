package com.cobblemon.mod.common.net.messages.server.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class StadiumPreviewChoicePacket(val battleId: UUID, val rqid: Int, val order: List<Int>) : NetworkPacket<StadiumPreviewChoicePacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(battleId)
        buffer.writeInt(rqid)
        buffer.writeByte(order.size)
        order.forEach(buffer::writeByte)
    }
    companion object {
        val ID = cobblemonResource("stadium_preview_choice")
        fun decode(buffer: RegistryFriendlyByteBuf): StadiumPreviewChoicePacket {
            val id = buffer.readUUID()
            val rqid = buffer.readInt()
            val size = buffer.readUnsignedByte().toInt()
            require(size in 1..6)
            return StadiumPreviewChoicePacket(id, rqid, List(size) { buffer.readUnsignedByte().toInt() })
        }
    }
}
