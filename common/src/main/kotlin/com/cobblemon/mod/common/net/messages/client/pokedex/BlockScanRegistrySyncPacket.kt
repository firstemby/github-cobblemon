package com.cobblemon.mod.common.net.messages.client.pokedex

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinitions
import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class BlockScanRegistrySyncPacket(defs: List<BlockScanDefinition>) :
    DataRegistrySyncPacket<BlockScanDefinition, BlockScanRegistrySyncPacket>(defs) {

    companion object {
        val ID = cobblemonResource("pokedex_block_scans")
        fun decode(buf: RegistryFriendlyByteBuf) =
            BlockScanRegistrySyncPacket(emptyList()).apply { decodeBuffer(buf) }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: BlockScanDefinition) {
        BlockScanDefinition.STREAM_CODEC.encode(buffer, entry)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): BlockScanDefinition {
        return BlockScanDefinition.STREAM_CODEC.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<BlockScanDefinition>) {
        BlockScanDefinitions.reloadEntries(entries)
    }
}
