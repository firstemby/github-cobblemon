package com.cobblemon.mod.common.net.messages.server.pokedex.scanner

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class RecordBlockScanPacket(val scanKey: ResourceLocation) : NetworkPacket<RecordBlockScanPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(scanKey)
    }

    companion object {
        val ID = cobblemonResource("record_block_scan")

        fun decode(buffer: RegistryFriendlyByteBuf) = RecordBlockScanPacket(buffer.readResourceLocation())
    }
}
