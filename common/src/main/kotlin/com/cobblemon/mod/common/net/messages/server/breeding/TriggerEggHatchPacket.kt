package com.cobblemon.mod.common.net.messages.server.breeding

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

class TriggerEggHatchPacket(val pos: BlockPos) : NetworkPacket<TriggerEggHatchPacket> {
    companion object {
        val ID = cobblemonResource("trigger_egg_hatch")

        fun decode(buf: RegistryFriendlyByteBuf) = TriggerEggHatchPacket(buf.readBlockPos())
    }

    override val id = ID
    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }
}
