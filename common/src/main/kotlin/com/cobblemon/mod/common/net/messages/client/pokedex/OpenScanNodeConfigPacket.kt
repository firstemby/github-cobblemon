package com.cobblemon.mod.common.net.messages.client.pokedex

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Server -> client: open the ScanNode config screen for the given entity, pre-filled with its config. */
class OpenScanNodeConfigPacket(
    val entityId: Int,
    val category: String,
    val scanId: String,
    val frames: List<String>
) : NetworkPacket<OpenScanNodeConfigPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(entityId)
        buffer.writeUtf(category)
        buffer.writeUtf(scanId)
        buffer.writeCollection(frames) { _, f -> buffer.writeUtf(f) }
    }

    companion object {
        val ID = cobblemonResource("open_scan_node_config")
        fun decode(buffer: RegistryFriendlyByteBuf) = OpenScanNodeConfigPacket(
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readUtf(),
            buffer.readList { it.readUtf() }
        )
    }
}
