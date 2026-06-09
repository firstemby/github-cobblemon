package com.cobblemon.mod.common.net.messages.server.pokedex.scanner

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Client -> server: apply edited config to a ScanNode entity. */
class SaveScanNodeConfigPacket(
    val entityId: Int,
    val category: String,
    val scanId: String,
    val frames: List<String>
) : NetworkPacket<SaveScanNodeConfigPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entityId)
        buffer.writeUtf(category)
        buffer.writeUtf(scanId)
        buffer.writeCollection(frames) { _, f -> buffer.writeUtf(f) }
    }

    companion object {
        val ID = cobblemonResource("save_scan_node_config")
        fun decode(buffer: RegistryFriendlyByteBuf) = SaveScanNodeConfigPacket(
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readList { it.readUtf() }
        )
    }
}
