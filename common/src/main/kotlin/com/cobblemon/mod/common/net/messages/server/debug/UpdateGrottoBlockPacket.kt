package com.cobblemon.mod.common.net.messages.server.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class UpdateGrottoBlockPacket(
    val grottoPos: BlockPos
) : NetworkPacket<UpdateGrottoBlockPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(grottoPos)
    }

    companion object {
        val ID = cobblemonResource("c2s_update_grotto_block")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateGrottoBlockPacket =
            UpdateGrottoBlockPacket(
                grottoPos = buffer.readBlockPos(),
            )
    }
}