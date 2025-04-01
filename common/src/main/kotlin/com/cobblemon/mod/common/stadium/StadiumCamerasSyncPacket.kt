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

    override val id = ID

    companion object {
        val ID = cobblemonResource("stadium_cameras_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumCamerasSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}