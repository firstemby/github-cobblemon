package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class StadiumCamerasSyncPacket(cameras: Collection<StadiumCameraEntry>) :
    DataRegistrySyncPacket<StadiumCameraEntry, StadiumCamerasSyncPacket>(cameras) {
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: StadiumCameraEntry) {
        entry.encode(buffer)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): StadiumCameraEntry? {
        return StadiumCameraEntry.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<StadiumCameraEntry>) {
        StadiumCameras.reload(entries.associateBy { it.id })
    }

    companion object {
        val ID = cobblemonResource("dex_entry_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = DexEntrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}