package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class StadiumCameraManagersSyncPacket(managers: Collection<StadiumCameraManagerEntry>) :
    DataRegistrySyncPacket<StadiumCameraManagerEntry, StadiumCameraManagersSyncPacket>(managers) {
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: StadiumCameraManagerEntry) {
        entry.encode(buffer)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): StadiumCameraManagerEntry? {
        return StadiumCameraManagerEntry.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<StadiumCameraManagerEntry>) {
        StadiumCameraManagers.reload(entries.associateBy { it.id })
    }

    override val id = ID

    companion object {
        val ID = cobblemonResource("stadium_camera_managers_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) = StadiumCameraManagersSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}