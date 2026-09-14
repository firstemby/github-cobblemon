package com.cobblemon.mod.common.net.messages.server.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class EditStadiumCinematicPacket(
    val requestId: UUID,
    val blockPos: BlockPos,
    val stadiumId: UUID,
    val revision: String = "",
    val source: String = ""
) : NetworkPacket<EditStadiumCinematicPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(requestId)
        buffer.writeBlockPos(blockPos)
        buffer.writeUUID(stadiumId)
        buffer.writeUtf(revision, 64)
        buffer.writeUtf(source, StadiumCinematic.MAX_LENGTH)
    }
    companion object {
        val ID = cobblemonResource("edit_stadium_cinematic")
        fun decode(buffer: RegistryFriendlyByteBuf) = EditStadiumCinematicPacket(buffer.readUUID(), buffer.readBlockPos(),
            buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(StadiumCinematic.MAX_LENGTH))
    }
}
